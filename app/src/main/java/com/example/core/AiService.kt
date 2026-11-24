package com.example.core

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import kotlin.math.min

private const val TAG = "StudoraAiService"
private const val DEFAULT_MODEL = "gemini-3.8-flash"

data class GeneratedQuizItem(
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

data class GeneratedFlashcardItem(
    val frontQuestion: String,
    val backAnswer: String
)

data class GeneratedStudySessionItem(
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val subjectName: String,
    val sessionType: String,
    val focusTopic: String
)

data class NutritionAnalysisResult(
    val extractedInfo: String,
    val aiSuggestions: String
)

class AiService(val isConfigured: Boolean = true) {

    companion object {
        const val AI_NOT_CONFIGURED_MESSAGE =
            "AI features are coming soon in this build. Firebase AI is not configured yet."
    }

    private fun requireConfiguration() {
        check(isConfigured) { AI_NOT_CONFIGURED_MESSAGE }
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            systemInstruction = content {
                text(
                    "You are STUDORA AI, an academic and student productivity companion. " +
                        "Follow the user's instructions strictly, preserve source meaning, " +
                        "be accurate and structured, support both clear English and Bangla when requested, " +
                        "and never fabricate facts absent from provided source materials."
                )
            }
        )
    }

    private val quizSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short descriptive quiz title"),
            "questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionText" to Schema.string(description = "The question prompt"),
                        "options" to Schema.array(
                            items = Schema.string(),
                            description = "Answer choices for MCQ or True/False; empty array for Short Answer or Viva"
                        ),
                        "correctAnswer" to Schema.string(description = "The exact correct answer or model answer"),
                        "explanation" to Schema.string(description = "Concise explanation of why the answer is correct")
                    )
                )
            )
        )
    )

    private val flashcardsSchema = Schema.obj(
        properties = mapOf(
            "cards" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "frontQuestion" to Schema.string(description = "Clear prompt or concept on front of card"),
                        "backAnswer" to Schema.string(description = "Concise accurate answer on back of card")
                    )
                )
            )
        )
    )

    private val studyPlanSchema = Schema.obj(
        properties = mapOf(
            "planTitle" to Schema.string(description = "Title for the personalized study routine"),
            "sessions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "dayOfWeek" to Schema.string(description = "Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, or Sunday"),
                        "startTime" to Schema.string(description = "Start time like 18:00"),
                        "endTime" to Schema.string(description = "End time like 18:50"),
                        "durationMinutes" to Schema.integer(description = "Session length in minutes"),
                        "subjectName" to Schema.string(description = "Subject or Break"),
                        "sessionType" to Schema.enumeration(listOf("STUDY", "BREAK", "REVISION"), "Type of block"),
                        "focusTopic" to Schema.string(description = "Specific study goal or revision focus")
                    )
                )
            )
        )
    )

    private val nutritionSchema = Schema.obj(
        properties = mapOf(
            "extractedInfo" to Schema.string(
                description = "Only food items, portions, or nutrition details explicitly visible in the chart/document. Do not invent values."
            ),
            "aiSuggestions" to Schema.string(
                description = "General educational meal-organization suggestions clearly labeled as general information, not medical advice."
            )
        )
    )

    fun streamChatMessage(
        historyMessages: List<Pair<String, String>>,
        userMessage: String,
        explicitContext: String? = null
    ): Flow<String> = flow {
        requireConfiguration()
        val history = historyMessages.takeLast(14).map { (role, text) ->
            content(role = if (role == "USER") "user" else "model") { text(text) }
        }
        val chat = generalModel.startChat(history = history)
        val fullPrompt = if (!explicitContext.isNullOrBlank()) {
            "User-selected study context:\n---\n$explicitContext\n---\n\nUser request:\n$userMessage"
        } else {
            userMessage
        }
        val buffer = StringBuilder()
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        buffer.append(piece)
                        emit(buffer.toString())
                    }
                }
            }
            if (buffer.isBlank()) {
                throw IllegalStateException("AI returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat timed out", e)
            throw IllegalStateException("Request timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            throw e
        }
    }

    /**
     * Generates study notes from a local PDF or Image document (chunking large PDFs)
     * or from text context, streaming real progress updates.
     */
    fun streamNotesFromDocument(
        documentFile: File?,
        fileType: String,
        userPrompt: String,
        fallbackTextContext: String? = null,
        onStatusUpdate: (String) -> Unit
    ): Flow<String> = flow {
        requireConfiguration()
        try {
            if (documentFile != null && documentFile.exists() && fileType == "PDF") {
                val pageCount = PdfUtils.getPdfPageCount(documentFile).getOrThrow()
                if (pageCount <= 0) throw IllegalStateException("PDF has no readable pages.")

                val maxPagesPerBatch = 5
                val totalToProcess = min(pageCount, 25)
                val chunkNotes = mutableListOf<String>()

                var start = 0
                var batchNum = 1
                val totalBatches = ((totalToProcess + maxPagesPerBatch - 1) / maxPagesPerBatch).coerceAtLeast(1)

                while (start < totalToProcess) {
                    val end = min(start + maxPagesPerBatch, totalToProcess)
                    onStatusUpdate("Reading PDF pages ${start + 1}–$end of $pageCount (batch $batchNum/$totalBatches)...")
                    val bitmaps = PdfUtils.renderPdfRangeToBitmaps(documentFile, start, end, 1000).getOrThrow()
                    val promptContent = content {
                        text(
                            "Analyze pages ${start + 1} to $end of this academic PDF. " +
                                "Follow the user's request strictly without inventing outside facts:\n" +
                                "User request: $userPrompt"
                        )
                        bitmaps.forEach { image(it) }
                    }

                    val responseText = if (totalBatches == 1) {
                        val acc = StringBuilder()
                        withTimeout(60_000L) {
                            generalModel.generateContentStream(promptContent).collect { chunk ->
                                val txt = chunk.text.orEmpty()
                                if (txt.isNotEmpty()) {
                                    acc.append(txt)
                                    emit(acc.toString())
                                }
                            }
                        }
                        bitmaps.forEach { it.recycle() }
                        acc.toString()
                    } else {
                        val res = withTimeout(60_000L) {
                            generalModel.generateContent(promptContent)
                        }
                        bitmaps.forEach { it.recycle() }
                        res.text.orEmpty()
                    }

                    if (responseText.isNotBlank()) {
                        chunkNotes.add(responseText)
                        if (totalBatches > 1) {
                            emit(chunkNotes.joinToString("\n\n---\n\n"))
                        }
                    }
                    start = end
                    batchNum++
                }

                if (chunkNotes.isEmpty() || chunkNotes.all { it.isBlank() }) {
                    throw IllegalStateException("AI could not extract notes from the document.")
                }
                if (totalBatches > 1 && pageCount > totalToProcess) {
                    emit(
                        chunkNotes.joinToString("\n\n---\n\n") +
                            "\n\n[Note: Processed the first $totalToProcess of $pageCount pages.]"
                    )
                }
            } else if (documentFile != null && documentFile.exists() && fileType == "IMAGE") {
                onStatusUpdate("Analyzing document image...")
                val bmp = PdfUtils.loadOrientedBitmap(documentFile, maxDimension = 1400).getOrThrow()
                val promptContent = content {
                    text("Analyze this study image and follow the user's request strictly:\n$userPrompt")
                    image(bmp)
                }
                val acc = StringBuilder()
                withTimeout(60_000L) {
                    generalModel.generateContentStream(promptContent).collect { chunk ->
                        val txt = chunk.text.orEmpty()
                        if (txt.isNotEmpty()) {
                            acc.append(txt)
                            emit(acc.toString())
                        }
                    }
                }
                bmp.recycle()
                if (acc.isBlank()) throw IllegalStateException("AI returned an empty response.")
            } else if (!fallbackTextContext.isNullOrBlank()) {
                onStatusUpdate("Generating study notes...")
                val acc = StringBuilder()
                withTimeout(60_000L) {
                    generalModel.generateContentStream(
                        "Source material:\n---\n$fallbackTextContext\n---\n\nUser request:\n$userPrompt"
                    ).collect { chunk ->
                        val txt = chunk.text.orEmpty()
                        if (txt.isNotEmpty()) {
                            acc.append(txt)
                            emit(acc.toString())
                        }
                    }
                }
                if (acc.isBlank()) throw IllegalStateException("AI returned an empty response.")
            } else {
                throw IllegalArgumentException("Source document file was not found on device.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Note generation timed out", e)
            throw IllegalStateException("AI note generation timed out. Try a shorter section or retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Note generation failed", e)
            throw e
        }
    }

    suspend fun generateQuiz(
        sourceText: String?,
        documentFile: File?,
        fileType: String,
        questionType: String,
        questionCount: Int,
        difficulty: String
    ): Result<Pair<String, List<GeneratedQuizItem>>> {
        return try {
            requireConfiguration()
            val model = Firebase.ai.generativeModel(
                modelName = DEFAULT_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = quizSchema
                }
            )

            val bitmapsToRecycle = mutableListOf<Bitmap>()
            val requestContent: Content = if (documentFile != null && documentFile.exists() && fileType == "PDF") {
                val pageCount = PdfUtils.getPdfPageCount(documentFile).getOrThrow()
                val bitmaps = PdfUtils.renderPdfRangeToBitmaps(documentFile, 0, min(pageCount, 6), 900).getOrThrow()
                bitmapsToRecycle.addAll(bitmaps)
                content {
                    text(
                        "Create exactly $questionCount $difficulty-level $questionType questions strictly from these pages. " +
                            "For MCQ provide 4 options; for TRUE_FALSE provide [\"True\", \"False\"]; for SHORT_ANSWER or VIVA provide empty options array []."
                    )
                    bitmaps.forEach { image(it) }
                }
            } else if (documentFile != null && documentFile.exists() && fileType == "IMAGE") {
                val bmp = PdfUtils.loadOrientedBitmap(documentFile, 1200).getOrThrow()
                bitmapsToRecycle.add(bmp)
                content {
                    text(
                        "Create exactly $questionCount $difficulty-level $questionType questions strictly from this image. " +
                            "For MCQ provide 4 options; for TRUE_FALSE provide [\"True\", \"False\"]; for SHORT_ANSWER or VIVA provide []."
                    )
                    image(bmp)
                }
            } else {
                val safeSource = sourceText?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalArgumentException("No source material selected."))
                content {
                    text(
                        "Create exactly $questionCount $difficulty-level $questionType questions strictly from this source:\n---\n$safeSource\n---\n" +
                            "For MCQ provide 4 options; for TRUE_FALSE provide [\"True\", \"False\"]; for SHORT_ANSWER or VIVA provide []."
                    )
                }
            }

            val response = try {
                withTimeout(60_000L) { model.generateContent(requestContent) }
            } finally {
                bitmapsToRecycle.forEach { it.recycle() }
            }

            val jsonStr = response.text ?: throw IllegalStateException("AI returned empty quiz JSON.")
            val json = JSONObject(jsonStr)
            val title = json.optString("title", "AI Study Quiz")
            val arr = json.getJSONArray("questions")
            val items = (0 until arr.length()).map { i ->
                val q = arr.getJSONObject(i)
                val optsArr = q.optJSONArray("options")
                val opts = if (optsArr != null) {
                    (0 until optsArr.length()).map { optsArr.getString(it) }
                } else emptyList()
                GeneratedQuizItem(
                    questionText = q.getString("questionText"),
                    options = opts,
                    correctAnswer = q.getString("correctAnswer"),
                    explanation = q.optString("explanation", "")
                )
            }
            if (items.isEmpty()) throw IllegalStateException("No quiz questions were generated.")
            Result.success(title to items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quiz generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateFlashcards(
        sourceText: String?,
        documentFile: File?,
        fileType: String,
        cardCount: Int
    ): Result<List<GeneratedFlashcardItem>> {
        return try {
            requireConfiguration()
            val model = Firebase.ai.generativeModel(
                modelName = DEFAULT_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = flashcardsSchema
                }
            )
            val bitmaps = mutableListOf<Bitmap>()
            val req = if (documentFile != null && documentFile.exists() && fileType == "PDF") {
                val pages = PdfUtils.getPdfPageCount(documentFile).getOrThrow()
                val rendered = PdfUtils.renderPdfRangeToBitmaps(documentFile, 0, min(pages, 6), 900).getOrThrow()
                bitmaps.addAll(rendered)
                content {
                    text("Generate $cardCount concise high-yield study flashcards (question on front, clear answer on back) strictly from these pages.")
                    rendered.forEach { image(it) }
                }
            } else if (documentFile != null && documentFile.exists() && fileType == "IMAGE") {
                val bmp = PdfUtils.loadOrientedBitmap(documentFile, 1200).getOrThrow()
                bitmaps.add(bmp)
                content {
                    text("Generate $cardCount concise high-yield study flashcards strictly from this image.")
                    image(bmp)
                }
            } else {
                val safeText = sourceText?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalArgumentException("No source text provided."))
                content {
                    text("Generate $cardCount concise high-yield study flashcards strictly from:\n$safeText")
                }
            }

            val response = try {
                withTimeout(60_000L) { model.generateContent(req) }
            } finally {
                bitmaps.forEach { it.recycle() }
            }

            val json = JSONObject(response.text ?: throw IllegalStateException("Empty response."))
            val arr = json.getJSONArray("cards")
            val cards = (0 until arr.length()).map { idx ->
                val c = arr.getJSONObject(idx)
                GeneratedFlashcardItem(
                    frontQuestion = c.getString("frontQuestion"),
                    backAnswer = c.getString("backAnswer")
                )
            }
            if (cards.isEmpty()) throw IllegalStateException("No flashcards generated.")
            Result.success(cards)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateStudyPlan(
        subjects: String,
        academicLevel: String,
        examInfo: String,
        dailyMinutes: Int,
        preferredHours: String,
        goals: String,
        difficultSubjects: String,
        breakPreference: String,
        weeklyAvailability: String
    ): Result<Pair<String, List<GeneratedStudySessionItem>>> {
        return try {
            requireConfiguration()
            val model = Firebase.ai.generativeModel(
                modelName = DEFAULT_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = studyPlanSchema
                }
            )
            val prompt = """
                Build a realistic, personalized 7-day weekly student study schedule strictly based on these inputs:
                - Subjects & Priorities: $subjects
                - Academic Level: $academicLevel
                - Exam Date / Timeline: $examInfo
                - Available Daily Study Time: $dailyMinutes minutes
                - Preferred Study Hours: $preferredHours
                - Difficult Subjects (give more time/revision): $difficultSubjects
                - Study Goals: $goals
                - Break Preference: $breakPreference
                - Weekly Availability: $weeklyAvailability

                Include balanced STUDY, BREAK, and REVISION blocks across the available days so daily study time stays realistic around $dailyMinutes minutes.
            """.trimIndent()

            val response = withTimeout(60_000L) { model.generateContent(prompt) }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty study plan response."))
            val title = json.optString("planTitle", "Personalized Study Routine")
            val arr = json.getJSONArray("sessions")
            val sessions = (0 until arr.length()).map { idx ->
                val s = arr.getJSONObject(idx)
                GeneratedStudySessionItem(
                    dayOfWeek = s.getString("dayOfWeek"),
                    startTime = s.getString("startTime"),
                    endTime = s.getString("endTime"),
                    durationMinutes = s.optInt("durationMinutes", 45),
                    subjectName = s.getString("subjectName"),
                    sessionType = s.optString("sessionType", "STUDY"),
                    focusTopic = s.optString("focusTopic", "")
                )
            }
            if (sessions.isEmpty()) throw IllegalStateException("No study sessions returned.")
            Result.success(title to sessions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Study plan generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeFoodChart(
        chartFile: File,
        fileType: String,
        userPrompt: String
    ): Result<NutritionAnalysisResult> {
        return try {
            requireConfiguration()
            val model = Firebase.ai.generativeModel(
                modelName = DEFAULT_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = nutritionSchema
                }
            )
            val bitmaps = mutableListOf<Bitmap>()
            val req = if (fileType == "PDF") {
                val pages = PdfUtils.getPdfPageCount(chartFile).getOrThrow()
                val rendered = PdfUtils.renderPdfRangeToBitmaps(chartFile, 0, min(pages, 4), 1000).getOrThrow()
                bitmaps.addAll(rendered)
                content {
                    text(
                        "Examine this food/nutrition chart. Strictly separate (1) extractedInfo: ONLY items and numbers visibly shown in the chart (do NOT invent calories/macros not present) and " +
                            "(2) aiSuggestions: general educational meal-organization tips addressing the user's request: $userPrompt. " +
                            "Remind clearly that this is general educational information, not medical advice."
                    )
                    rendered.forEach { image(it) }
                }
            } else {
                val bmp = PdfUtils.loadOrientedBitmap(chartFile, 1300).getOrThrow()
                bitmaps.add(bmp)
                content {
                    text(
                        "Examine this food/nutrition chart. Strictly separate (1) extractedInfo: ONLY items and numbers visibly shown in the image (do NOT invent nutrition values) and " +
                            "(2) aiSuggestions: general educational meal-organization tips addressing: $userPrompt. " +
                            "Include a clear note that this is general educational guidance, not medical diagnosis or professional advice."
                    )
                    image(bmp)
                }
            }

            val response = try {
                withTimeout(60_000L) { model.generateContent(req) }
            } finally {
                bitmaps.forEach { it.recycle() }
            }

            val json = JSONObject(response.text ?: throw IllegalStateException("Empty nutrition analysis response."))
            Result.success(
                NutritionAnalysisResult(
                    extractedInfo = json.getString("extractedInfo"),
                    aiSuggestions = json.getString("aiSuggestions")
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Nutrition analysis failed", e)
            Result.failure(e)
        }
    }
}
