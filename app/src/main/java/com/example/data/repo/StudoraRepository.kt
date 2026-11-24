package com.example.data.repo

import android.content.Context
import android.net.Uri
import android.util.Patterns
import androidx.room.withTransaction
import com.example.core.AiService
import com.example.core.AudioService
import com.example.core.BackupManager
import com.example.core.BackupSummary
import com.example.core.ConnectivityObserver
import com.example.core.LocalStorageManager
import com.example.core.NutritionAnalysisResult
import com.example.core.PdfUtils
import com.example.core.StorageStats
import com.example.data.db.*
import com.example.notifications.ReminderScheduler
import com.example.notifications.ScheduleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class StudoraRepository(
    private val context: Context,
    private val db: StudoraDatabase,
    val storage: LocalStorageManager,
    val reminderScheduler: ReminderScheduler,
    val aiService: AiService,
    val audioService: AudioService,
    val backupManager: BackupManager,
    val connectivityObserver: ConnectivityObserver
) {
    private val settingsDao = db.settingsDao()
    private val academicDao = db.academicDao()
    private val learningDao = db.learningDao()
    private val productivityDao = db.productivityDao()
    private val lifestyleDao = db.lifestyleDao()
    private val aiDao = db.aiDao()

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline

    // ---- Settings & Profile ----
    val settings: Flow<AppSettingsEntity> = settingsDao.observeSettings().map { it ?: AppSettingsEntity() }
    val profile: Flow<LocalProfileEntity> = profileFlow()

    private fun profileFlow(): Flow<LocalProfileEntity> =
        settingsDao.observeProfile().map { it ?: LocalProfileEntity() }

    suspend fun saveSettings(settings: AppSettingsEntity) {
        settingsDao.upsertSettings(settings.copy(id = 1, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveProfile(profile: LocalProfileEntity) {
        settingsDao.upsertProfile(profile.copy(id = 1, updatedAt = System.currentTimeMillis()))
    }

    // ---- Subjects & Topics ----
    val subjects: Flow<List<SubjectEntity>> = academicDao.observeSubjects()
    val allTopics: Flow<List<TopicEntity>> = academicDao.observeAllTopics()
    val allDocuments: Flow<List<DocumentEntity>> = academicDao.observeAllDocuments()
    val allWrittenNotes: Flow<List<WrittenNoteEntity>> = academicDao.observeAllWrittenNotes()

    fun observeSubject(id: Long): Flow<SubjectEntity?> = academicDao.observeSubject(id)
    fun observeTopics(subjectId: Long): Flow<List<TopicEntity>> = academicDao.observeTopicsForSubject(subjectId)
    fun observeDocumentsForSubject(subjectId: Long): Flow<List<DocumentEntity>> = academicDao.observeDocumentsForSubject(subjectId)
    fun observeDocumentsForTopic(topicId: Long): Flow<List<DocumentEntity>> = academicDao.observeDocumentsForTopic(topicId)
    fun observeWrittenNotesForSubject(subjectId: Long): Flow<List<WrittenNoteEntity>> = academicDao.observeWrittenNotesForSubject(subjectId)
    fun observeWrittenNotesForTopic(topicId: Long): Flow<List<WrittenNoteEntity>> = academicDao.observeWrittenNotesForTopic(topicId)

    suspend fun touchSubject(id: Long) = academicDao.touchSubject(id)

    suspend fun saveSubject(subject: SubjectEntity): Long {
        val now = System.currentTimeMillis()
        return if (subject.id == 0L) {
            academicDao.insertSubject(subject.copy(createdAt = now, updatedAt = now, lastAccessedAt = now))
        } else {
            academicDao.updateSubject(subject.copy(updatedAt = now))
            subject.id
        }
    }

    suspend fun deleteSubjectAndFiles(subject: SubjectEntity) {
        val docs = academicDao.getDocumentsBySubjectSync(subject.id)
        db.withTransaction {
            academicDao.deleteSubject(subject)
        }
        docs.forEach { storage.deleteFileSafely(it.filePath) }
    }

    suspend fun saveTopic(topic: TopicEntity): Long {
        val now = System.currentTimeMillis()
        return if (topic.id == 0L) {
            academicDao.insertTopic(topic.copy(createdAt = now, updatedAt = now))
        } else {
            academicDao.updateTopic(topic.copy(updatedAt = now))
            topic.id
        }
    }

    suspend fun reorderTopics(topics: List<TopicEntity>) {
        val updated = topics.mapIndexed { index, t ->
            t.copy(displayOrder = index, updatedAt = System.currentTimeMillis())
        }
        academicDao.updateTopics(updated)
    }

    suspend fun deleteTopic(topic: TopicEntity) = academicDao.deleteTopic(topic)

    // ---- Documents (Import / Scan / Rename / Delete) ----
    suspend fun getDocument(id: Long): DocumentEntity? = academicDao.getDocument(id)

    suspend fun importDocumentFromUri(
        uri: Uri,
        title: String,
        subjectId: Long,
        topicId: Long?
    ): Result<DocumentEntity> {
        val mime = storage.getMimeType(uri)
        val name = storage.getDisplayNameFromUri(uri)
        val isPdf = mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
        val isImage = mime.startsWith("image/", ignoreCase = true)
        if (!isPdf && !isImage) {
            return Result.failure(
                IllegalArgumentException("Unsupported file type ($mime). Please choose a PDF or Image file.")
            )
        }
        val copied = storage.copyUriToDirectory(
            uri = uri,
            targetDir = storage.documentsDir,
            preferredExtension = if (isPdf) "pdf" else null
        ).getOrElse { return Result.failure(it) }

        val pageCount = if (isPdf) {
            PdfUtils.getPdfPageCount(copied).getOrElse {
                copied.delete()
                return Result.failure(it)
            }
        } else 1

        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            subjectId = subjectId,
            topicId = topicId,
            title = title.trim().ifBlank { name.substringBeforeLast('.') },
            filePath = copied.absolutePath,
            fileType = if (isPdf) "PDF" else "IMAGE",
            mimeType = if (isPdf) "application/pdf" else mime,
            fileSizeBytes = copied.length(),
            pageCount = pageCount,
            sourceType = "IMPORT",
            createdAt = now,
            updatedAt = now
        )
        val id = academicDao.insertDocument(entity)
        academicDao.touchSubject(subjectId, now)
        return Result.success(entity.copy(id = id))
    }

    suspend fun saveScannedPdfDocument(
        pageFiles: List<File>,
        title: String,
        subjectId: Long,
        topicId: Long?
    ): Result<DocumentEntity> {
        val pdfFile = storage.createNewFile(storage.documentsDir, "scan", "pdf")
        val pageCount = PdfUtils.createPdfFromImages(pageFiles, pdfFile).getOrElse {
            return Result.failure(it)
        }
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            subjectId = subjectId,
            topicId = topicId,
            title = title.trim().ifBlank { "Scanned Document" },
            filePath = pdfFile.absolutePath,
            fileType = "PDF",
            mimeType = "application/pdf",
            fileSizeBytes = pdfFile.length(),
            pageCount = pageCount,
            sourceType = "SCAN",
            createdAt = now,
            updatedAt = now
        )
        val id = academicDao.insertDocument(entity)
        academicDao.touchSubject(subjectId, now)
        return Result.success(entity.copy(id = id))
    }

    suspend fun renameDocument(doc: DocumentEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        academicDao.updateDocument(doc.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(doc: DocumentEntity) {
        db.withTransaction { academicDao.deleteDocument(doc) }
        storage.deleteFileSafely(doc.filePath)
    }

    // ---- Written Notes ----
    suspend fun getWrittenNote(id: Long): WrittenNoteEntity? = academicDao.getWrittenNote(id)

    suspend fun saveWrittenNote(note: WrittenNoteEntity): Long {
        val now = System.currentTimeMillis()
        val id = if (note.id == 0L) {
            academicDao.insertWrittenNote(note.copy(createdAt = now, updatedAt = now))
        } else {
            academicDao.updateWrittenNote(note.copy(updatedAt = now))
            note.id
        }
        academicDao.touchSubject(note.subjectId, now)
        return id
    }

    suspend fun deleteWrittenNote(note: WrittenNoteEntity) = academicDao.deleteWrittenNote(note)

    // ---- AI Notes, Voice Notes, AI Audio, Quizzes, Flashcards ----
    val allAiNotes: Flow<List<AiNoteEntity>> = learningDao.observeAllAiNotes()
    val allRecordings: Flow<List<AudioRecordingEntity>> = learningDao.observeAllRecordings()
    val allAiAudio: Flow<List<AiAudioEntity>> = learningDao.observeAllAiAudio()
    val allQuizzes: Flow<List<QuizEntity>> = learningDao.observeAllQuizzes()
    val allQuizAttempts: Flow<List<QuizAttemptEntity>> = learningDao.observeAllAttempts()
    val allFlashcards: Flow<List<FlashcardEntity>> = learningDao.observeAllFlashcards()

    suspend fun saveAiNote(note: AiNoteEntity): Long {
        val now = System.currentTimeMillis()
        return if (note.id == 0L) {
            learningDao.insertAiNote(note.copy(createdAt = now, updatedAt = now))
        } else {
            learningDao.updateAiNote(note.copy(updatedAt = now))
            note.id
        }
    }

    suspend fun deleteAiNote(note: AiNoteEntity) = learningDao.deleteAiNote(note)

    suspend fun saveRecording(rec: AudioRecordingEntity): Long {
        val now = System.currentTimeMillis()
        return if (rec.id == 0L) {
            learningDao.insertRecording(rec.copy(createdAt = now, updatedAt = now))
        } else {
            learningDao.updateRecording(rec.copy(updatedAt = now))
            rec.id
        }
    }

    suspend fun deleteRecording(rec: AudioRecordingEntity) {
        db.withTransaction { learningDao.deleteRecording(rec) }
        storage.deleteFileSafely(rec.filePath)
    }

    suspend fun generateAndSaveAiAudio(
        title: String,
        text: String,
        sourceType: String,
        sourceId: Long?,
        subjectId: Long?,
        topicId: Long?,
        languageTag: String,
        speechRate: Float
    ): Result<AiAudioEntity> {
        val (file, durationMs) = audioService.synthesizeNoteToLocalAudio(
            text = text,
            languageTag = languageTag,
            speechRate = speechRate
        ).getOrElse { return Result.failure(it) }

        val now = System.currentTimeMillis()
        val entity = AiAudioEntity(
            subjectId = subjectId,
            topicId = topicId,
            sourceType = sourceType,
            sourceId = sourceId,
            title = title.trim().ifBlank { "AI Audio Note" },
            sourceTextPreview = text.take(180),
            filePath = file.absolutePath,
            languageCode = languageTag,
            voiceName = languageTag,
            speechRate = speechRate,
            durationMs = durationMs,
            createdAt = now,
            updatedAt = now
        )
        val id = learningDao.insertAiAudio(entity)
        return Result.success(entity.copy(id = id))
    }

    suspend fun deleteAiAudio(audio: AiAudioEntity) {
        db.withTransaction { learningDao.deleteAiAudio(audio) }
        storage.deleteFileSafely(audio.filePath)
    }

    fun observeQuestionsForQuiz(quizId: Long): Flow<List<QuizQuestionEntity>> =
        learningDao.observeQuestionsForQuiz(quizId)

    fun observeAttemptsForQuiz(quizId: Long): Flow<List<QuizAttemptEntity>> =
        learningDao.observeAttemptsForQuiz(quizId)

    suspend fun generateAndSaveQuiz(
        subjectId: Long?,
        topicId: Long?,
        documentId: Long?,
        sourceText: String?,
        questionType: String,
        questionCount: Int,
        difficulty: String
    ): Result<Long> {
        val doc = documentId?.let { academicDao.getDocument(it) }
        val docFile = doc?.let { File(it.filePath) }
        val (title, items) = aiService.generateQuiz(
            sourceText = sourceText,
            documentFile = docFile,
            fileType = doc?.fileType ?: "TEXT",
            questionType = questionType,
            questionCount = questionCount,
            difficulty = difficulty
        ).getOrElse { return Result.failure(it) }

        val quizId = db.withTransaction {
            val qId = learningDao.insertQuiz(
                QuizEntity(
                    subjectId = subjectId,
                    topicId = topicId,
                    documentId = documentId,
                    title = title,
                    questionType = questionType,
                    difficulty = difficulty,
                    questionCount = items.size
                )
            )
            val entities = items.mapIndexed { index, item ->
                QuizQuestionEntity(
                    quizId = qId,
                    questionText = item.questionText,
                    questionType = questionType,
                    optionsJson = JSONArray(item.options).toString(),
                    correctAnswer = item.correctAnswer,
                    explanation = item.explanation,
                    orderIndex = index
                )
            }
            learningDao.insertQuizQuestions(entities)
            qId
        }
        return Result.success(quizId)
    }

    suspend fun saveQuizAttempt(quizId: Long, score: Int, total: Int, answers: Map<Long, String>) {
        val obj = JSONObject()
        answers.forEach { (k, v) -> obj.put(k.toString(), v) }
        learningDao.insertQuizAttempt(
            QuizAttemptEntity(
                quizId = quizId,
                score = score,
                totalQuestions = total,
                answersJson = obj.toString()
            )
        )
    }

    suspend fun deleteQuiz(quiz: QuizEntity) = learningDao.deleteQuiz(quiz)

    suspend fun generateAndSaveFlashcards(
        subjectId: Long?,
        topicId: Long?,
        documentId: Long?,
        sourceText: String?,
        count: Int
    ): Result<Int> {
        val doc = documentId?.let { academicDao.getDocument(it) }
        val docFile = doc?.let { File(it.filePath) }
        val cards = aiService.generateFlashcards(
            sourceText = sourceText,
            documentFile = docFile,
            fileType = doc?.fileType ?: "TEXT",
            cardCount = count
        ).getOrElse { return Result.failure(it) }

        val now = System.currentTimeMillis()
        val entities = cards.map {
            FlashcardEntity(
                subjectId = subjectId,
                topicId = topicId,
                documentId = documentId,
                frontQuestion = it.frontQuestion,
                backAnswer = it.backAnswer,
                masteryStatus = "NEW",
                createdAt = now,
                updatedAt = now
            )
        }
        learningDao.insertFlashcards(entities)
        return Result.success(entities.size)
    }

    suspend fun saveFlashcard(card: FlashcardEntity): Long {
        val now = System.currentTimeMillis()
        return if (card.id == 0L) {
            learningDao.insertFlashcard(card.copy(createdAt = now, updatedAt = now))
        } else {
            learningDao.updateFlashcard(card.copy(updatedAt = now))
            card.id
        }
    }

    suspend fun updateFlashcardReview(card: FlashcardEntity, masteryStatus: String) {
        val now = System.currentTimeMillis()
        learningDao.updateFlashcard(
            card.copy(
                masteryStatus = masteryStatus,
                reviewCount = card.reviewCount + 1,
                lastReviewedAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteFlashcard(card: FlashcardEntity) = learningDao.deleteFlashcard(card)

    // ---- Tasks & Native Notifications ----
    val allTasks: Flow<List<TaskEntity>> = productivityDao.observeAllTasks()

    suspend fun saveTaskWithNotification(task: TaskEntity): Pair<Long, ScheduleResult?> {
        val now = System.currentTimeMillis()
        val taskId = if (task.id == 0L) {
            productivityDao.insertTask(task.copy(createdAt = now, updatedAt = now))
        } else {
            productivityDao.updateTask(task.copy(updatedAt = now))
            task.id
        }

        val existingRem = productivityDao.getReminderForTask(taskId)
        val notifId = existingRem?.notificationId ?: (100_000 + (taskId % 50_000).toInt())

        var scheduleResult: ScheduleResult? = null
        if (task.status == "PENDING" && task.isReminderEnabled && task.reminderTimeMinutes != null) {
            val triggerMillis = computeDateTimeMillis(task.taskDateMillis, task.reminderTimeMinutes)
            scheduleResult = reminderScheduler.scheduleTaskReminder(
                taskId = taskId,
                notificationId = notifId,
                title = task.title,
                description = task.description,
                triggerAtMillis = triggerMillis
            )
            productivityDao.upsertTaskReminder(
                TaskReminderEntity(
                    id = existingRem?.id ?: 0L,
                    taskId = taskId,
                    notificationId = notifId,
                    triggerAtMillis = triggerMillis,
                    isScheduled = scheduleResult.scheduled,
                    updatedAt = now
                )
            )
        } else {
            if (existingRem != null) {
                reminderScheduler.cancelReminder(ReminderScheduler.TYPE_TASK, taskId, existingRem.notificationId)
                productivityDao.deleteRemindersForTask(taskId)
            }
        }
        return taskId to scheduleResult
    }

    suspend fun toggleTaskComplete(task: TaskEntity) {
        val newStatus = if (task.status == "COMPLETED") "PENDING" else "COMPLETED"
        saveTaskWithNotification(task.copy(status = newStatus))
    }

    suspend fun deleteTask(task: TaskEntity) {
        val rem = productivityDao.getReminderForTask(task.id)
        if (rem != null) {
            reminderScheduler.cancelReminder(ReminderScheduler.TYPE_TASK, task.id, rem.notificationId)
        }
        db.withTransaction { productivityDao.deleteTask(task) }
        storage.deleteFileSafely(task.imagePath)
    }

    // ---- Bills & Native Notifications ----
    val allBills: Flow<List<BillEntity>> = productivityDao.observeAllBills()

    suspend fun saveBillWithNotification(bill: BillEntity): Pair<Long, ScheduleResult?> {
        val now = System.currentTimeMillis()
        val billId = if (bill.id == 0L) {
            productivityDao.insertBill(bill.copy(createdAt = now, updatedAt = now))
        } else {
            productivityDao.updateBill(bill.copy(updatedAt = now))
            bill.id
        }

        val existingRem = productivityDao.getReminderForBill(billId)
        val notifId = existingRem?.notificationId ?: (200_000 + (billId % 50_000).toInt())

        var scheduleResult: ScheduleResult? = null
        if (bill.status == "PENDING") {
            val triggerMillis = computeBillReminderMillis(bill)
            scheduleResult = reminderScheduler.scheduleBillReminder(
                billId = billId,
                notificationId = notifId,
                title = bill.title,
                amountFormatted = String.format("%.2f", bill.amount),
                triggerAtMillis = triggerMillis
            )
            productivityDao.upsertBillReminder(
                BillReminderEntity(
                    id = existingRem?.id ?: 0L,
                    billId = billId,
                    notificationId = notifId,
                    triggerAtMillis = triggerMillis,
                    isScheduled = scheduleResult.scheduled,
                    updatedAt = now
                )
            )
        } else if (existingRem != null) {
            reminderScheduler.cancelReminder(ReminderScheduler.TYPE_BILL, billId, existingRem.notificationId)
            productivityDao.deleteRemindersForBill(billId)
        }
        return billId to scheduleResult
    }

    suspend fun toggleBillPaid(bill: BillEntity) {
        val newStatus = if (bill.status == "PAID") "PENDING" else "PAID"
        saveBillWithNotification(bill.copy(status = newStatus))
    }

    suspend fun deleteBill(bill: BillEntity) {
        val rem = productivityDao.getReminderForBill(bill.id)
        if (rem != null) {
            reminderScheduler.cancelReminder(ReminderScheduler.TYPE_BILL, bill.id, rem.notificationId)
        }
        productivityDao.deleteBill(bill)
    }

    // ---- Self Study Routine ----
    val studyPlans: Flow<List<StudyPlanEntity>> = productivityDao.observeStudyPlans()
    val allStudySessions: Flow<List<StudySessionEntity>> = productivityDao.observeAllStudySessions()

    suspend fun generateAndSaveAiStudyPlan(
        subjects: String,
        academicLevel: String,
        examDateMillis: Long?,
        examInfo: String,
        dailyMinutes: Int,
        preferredHours: String,
        goals: String,
        difficultSubjects: String,
        breakPreference: String,
        weeklyAvailability: String
    ): Result<Long> {
        val (title, sessions) = aiService.generateStudyPlan(
            subjects = subjects,
            academicLevel = academicLevel,
            examInfo = examInfo,
            dailyMinutes = dailyMinutes,
            preferredHours = preferredHours,
            goals = goals,
            difficultSubjects = difficultSubjects,
            breakPreference = breakPreference,
            weeklyAvailability = weeklyAvailability
        ).getOrElse { return Result.failure(it) }

        val planId = db.withTransaction {
            val pId = productivityDao.insertStudyPlan(
                StudyPlanEntity(
                    title = title,
                    subjectsSummary = subjects,
                    academicLevel = academicLevel,
                    examDateMillis = examDateMillis,
                    dailyStudyMinutes = dailyMinutes,
                    preferredHours = preferredHours,
                    studyGoals = goals,
                    difficultSubjects = difficultSubjects,
                    breakPreferences = breakPreference,
                    isAiGenerated = true
                )
            )
            val entities = sessions.mapIndexed { idx, s ->
                StudySessionEntity(
                    planId = pId,
                    dayOfWeek = s.dayOfWeek,
                    startTime = s.startTime,
                    endTime = s.endTime,
                    durationMinutes = s.durationMinutes,
                    subjectName = s.subjectName,
                    sessionType = s.sessionType,
                    focusTopic = s.focusTopic,
                    orderIndex = idx
                )
            }
            productivityDao.insertStudySessions(entities)
            pId
        }
        return Result.success(planId)
    }

    suspend fun createManualStudyPlan(title: String, subjectsSummary: String): Long {
        return productivityDao.insertStudyPlan(
            StudyPlanEntity(
                title = title.ifBlank { "My Study Routine" },
                subjectsSummary = subjectsSummary,
                isAiGenerated = false
            )
        )
    }

    suspend fun saveStudySession(session: StudySessionEntity): Long {
        return if (session.id == 0L) {
            productivityDao.insertStudySession(session)
        } else {
            productivityDao.updateStudySession(session)
            session.id
        }
    }

    suspend fun toggleStudySessionComplete(session: StudySessionEntity) {
        val nowDone = !session.isCompleted
        productivityDao.updateStudySession(
            session.copy(
                isCompleted = nowDone,
                completedAt = if (nowDone) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun deleteStudySession(session: StudySessionEntity) = productivityDao.deleteStudySession(session)
    suspend fun deleteStudyPlan(plan: StudyPlanEntity) = productivityDao.deleteStudyPlan(plan)

    // ---- Wallet ----
    val walletTransactions: Flow<List<WalletTransactionEntity>> = lifestyleDao.observeWalletTransactions()

    suspend fun saveWalletTransaction(tx: WalletTransactionEntity) {
        val now = System.currentTimeMillis()
        if (tx.id == 0L) {
            lifestyleDao.insertTransaction(tx.copy(createdAt = now, updatedAt = now))
        } else {
            lifestyleDao.updateTransaction(tx.copy(updatedAt = now))
        }
    }

    suspend fun deleteWalletTransaction(tx: WalletTransactionEntity) = lifestyleDao.deleteTransaction(tx)

    // ---- Class Routine ----
    val classRoutines: Flow<List<ClassRoutineEntity>> = lifestyleDao.observeClassRoutines()
    val timetableEntries: Flow<List<TimetableEntryEntity>> = lifestyleDao.observeTimetableEntries()

    suspend fun saveClassRoutineFile(title: String, file: File, fileType: String, pageCount: Int = 1): Long {
        val now = System.currentTimeMillis()
        return lifestyleDao.insertClassRoutine(
            ClassRoutineEntity(
                title = title,
                filePath = file.absolutePath,
                fileType = fileType,
                pageCount = pageCount,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateClassRoutine(routine: ClassRoutineEntity) {
        lifestyleDao.updateClassRoutine(routine.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteClassRoutine(routine: ClassRoutineEntity) {
        db.withTransaction { lifestyleDao.deleteClassRoutine(routine) }
        storage.deleteFileSafely(routine.filePath)
    }

    suspend fun saveTimetableEntry(entry: TimetableEntryEntity) {
        val now = System.currentTimeMillis()
        if (entry.id == 0L) {
            lifestyleDao.insertTimetableEntry(entry.copy(createdAt = now, updatedAt = now))
        } else {
            lifestyleDao.updateTimetableEntry(entry.copy(updatedAt = now))
        }
    }

    suspend fun deleteTimetableEntry(entry: TimetableEntryEntity) = lifestyleDao.deleteTimetableEntry(entry)

    // ---- Bus Schedule ----
    val busSchedules: Flow<List<BusScheduleEntity>> = lifestyleDao.observeBusSchedules()

    suspend fun saveBusSchedule(schedule: BusScheduleEntity) {
        val now = System.currentTimeMillis()
        if (schedule.id == 0L) {
            lifestyleDao.insertBusSchedule(schedule.copy(createdAt = now, updatedAt = now))
        } else {
            lifestyleDao.updateBusSchedule(schedule.copy(updatedAt = now))
        }
    }

    suspend fun deleteBusSchedule(schedule: BusScheduleEntity) {
        db.withTransaction { lifestyleDao.deleteBusSchedule(schedule) }
        if (schedule.filePath.isNotBlank()) storage.deleteFileSafely(schedule.filePath)
    }

    // ---- Nutrition Guide ----
    val foodCharts: Flow<List<FoodChartEntity>> = lifestyleDao.observeFoodCharts()
    val nutritionSuggestions: Flow<List<NutritionSuggestionEntity>> = lifestyleDao.observeNutritionSuggestions()

    suspend fun saveFoodChart(title: String, file: File, fileType: String, pageCount: Int = 1): Long {
        val now = System.currentTimeMillis()
        return lifestyleDao.insertFoodChart(
            FoodChartEntity(
                title = title,
                filePath = file.absolutePath,
                fileType = fileType,
                pageCount = pageCount,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteFoodChart(chart: FoodChartEntity) {
        db.withTransaction { lifestyleDao.deleteFoodChart(chart) }
        storage.deleteFileSafely(chart.filePath)
    }

    suspend fun analyzeAndSaveFoodChart(
        chart: FoodChartEntity,
        prompt: String
    ): Result<NutritionSuggestionEntity> {
        val file = File(chart.filePath)
        if (!file.exists()) return Result.failure(IllegalStateException("Food chart file not found on device."))
        val res: NutritionAnalysisResult = aiService.analyzeFoodChart(file, chart.fileType, prompt)
            .getOrElse { return Result.failure(it) }
        val entity = NutritionSuggestionEntity(
            foodChartId = chart.id,
            title = "${chart.title} Analysis",
            prompt = prompt,
            extractedInfo = res.extractedInfo,
            aiSuggestions = res.aiSuggestions
        )
        val id = lifestyleDao.insertNutritionSuggestion(entity)
        return Result.success(entity.copy(id = id))
    }

    suspend fun deleteNutritionSuggestion(s: NutritionSuggestionEntity) = lifestyleDao.deleteNutritionSuggestion(s)

    // ---- Emergency Contacts ----
    val emergencyContacts: Flow<List<EmergencyContactEntity>> = lifestyleDao.observeEmergencyContacts()

    suspend fun saveEmergencyContact(contact: EmergencyContactEntity) {
        val now = System.currentTimeMillis()
        if (contact.id == 0L) {
            lifestyleDao.insertEmergencyContact(contact.copy(createdAt = now, updatedAt = now))
        } else {
            lifestyleDao.updateEmergencyContact(contact.copy(updatedAt = now))
        }
    }

    suspend fun deleteEmergencyContact(contact: EmergencyContactEntity) = lifestyleDao.deleteEmergencyContact(contact)

    // ---- My Portal ----
    val portalEntries: Flow<List<PortalEntryEntity>> = lifestyleDao.observePortalEntries()

    fun validateAndNormalizeUrl(rawInput: String): Result<String> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Website URL is required."))

        val lower = trimmed.lowercase()
        val forbiddenSchemes = listOf("javascript:", "file:", "data:", "content:", "vbscript:", "about:")
        if (forbiddenSchemes.any { lower.startsWith(it) }) {
            return Result.failure(IllegalArgumentException("Unsafe URL scheme is not allowed. Use http:// or https://."))
        }

        val normalized = if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val uri = try {
            Uri.parse(normalized)
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("Invalid website URL format."))
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme != "https" && scheme != "http") || host.isNullOrBlank() || !host.contains('.')) {
            return Result.failure(IllegalArgumentException("Please enter a valid domain or URL (e.g., https://github.com)."))
        }
        if (!Patterns.WEB_URL.matcher(normalized).matches()) {
            return Result.failure(IllegalArgumentException("Malformed website URL. Please check spelling."))
        }
        return Result.success(normalized)
    }

    suspend fun savePortalEntry(entry: PortalEntryEntity): Result<Long> {
        val validUrl = validateAndNormalizeUrl(entry.websiteUrl).getOrElse {
            return Result.failure(it)
        }
        val now = System.currentTimeMillis()
        val cachedIcon = if (connectivityObserver.checkCurrentConnectivity()) {
            storage.downloadAndCacheFavicon(validUrl) ?: entry.customIconPath
        } else {
            entry.customIconPath
        }
        val cleaned = entry.copy(
            websiteName = entry.websiteName.trim(),
            websiteUrl = validUrl,
            customIconPath = cachedIcon,
            updatedAt = now
        )
        val id = if (cleaned.id == 0L) {
            lifestyleDao.insertPortalEntry(cleaned.copy(createdAt = now))
        } else {
            lifestyleDao.updatePortalEntry(cleaned)
            cleaned.id
        }
        return Result.success(id)
    }

    suspend fun markPortalOpened(entry: PortalEntryEntity) {
        lifestyleDao.updatePortalEntry(entry.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun reorderPortals(entries: List<PortalEntryEntity>) {
        val updated = entries.mapIndexed { idx, e -> e.copy(displayOrder = idx) }
        lifestyleDao.updatePortalEntries(updated)
    }

    suspend fun deletePortalEntry(entry: PortalEntryEntity) = lifestyleDao.deletePortalEntry(entry)

    // ---- Studora AI Conversations & Pending Queue ----
    val aiConversations: Flow<List<AiConversationEntity>> = aiDao.observeConversations()
    val pendingAiRequests: Flow<List<PendingAiRequestEntity>> = aiDao.observePendingRequests()

    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>> = aiDao.observeMessages(conversationId)

    suspend fun createConversation(
        title: String,
        contextType: String? = null,
        contextId: Long? = null,
        contextLabel: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        return aiDao.insertConversation(
            AiConversationEntity(
                title = title.ifBlank { "New AI Conversation" },
                contextType = contextType,
                contextId = contextId,
                contextLabel = contextLabel,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateConversationContext(
        conversationId: Long,
        contextType: String?,
        contextId: Long?,
        contextLabel: String?
    ) {
        val conv = aiDao.getConversation(conversationId) ?: return
        aiDao.updateConversation(
            conv.copy(
                contextType = contextType,
                contextId = contextId,
                contextLabel = contextLabel,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun insertMessage(msg: AiMessageEntity): Long {
        val id = aiDao.insertMessage(msg)
        aiDao.getConversation(msg.conversationId)?.let { conv ->
            val updatedTitle = if (conv.title == "New AI Conversation" && msg.role == "USER") {
                msg.content.trim().take(42)
            } else conv.title
            aiDao.updateConversation(conv.copy(title = updatedTitle, updatedAt = System.currentTimeMillis()))
        }
        return id
    }

    suspend fun getMessagesSync(conversationId: Long): List<AiMessageEntity> =
        aiDao.getMessagesSync(conversationId)

    suspend fun deleteConversation(conv: AiConversationEntity) = aiDao.deleteConversation(conv)

    suspend fun queuePendingAiRequest(
        conversationId: Long?,
        prompt: String,
        contextSummary: String,
        contextPayload: String
    ): Long {
        return aiDao.insertPendingRequest(
            PendingAiRequestEntity(
                conversationId = conversationId,
                prompt = prompt,
                contextSummary = contextSummary,
                contextPayload = contextPayload,
                status = "PENDING"
            )
        )
    }

    suspend fun deletePendingAiRequest(req: PendingAiRequestEntity) = aiDao.deletePendingRequest(req)

    suspend fun buildExplicitContextText(contextType: String, contextId: Long): Pair<String, String> {
        return withContext(Dispatchers.IO) {
            when (contextType) {
                "SUBJECT" -> {
                    val s = academicDao.getSubject(contextId) ?: return@withContext "" to ""
                    val label = "Subject: ${s.name}"
                    val payload = "Subject: ${s.name}\nCode: ${s.code}\nTeacher: ${s.teacherName}\nLevel: ${s.academicLevel}\nDescription: ${s.description}"
                    label to payload
                }
                "TOPIC" -> {
                    val t = academicDao.getTopic(contextId) ?: return@withContext "" to ""
                    val s = academicDao.getSubject(t.subjectId)
                    val label = "Topic: ${t.title}"
                    val payload = "Subject: ${s?.name.orEmpty()}\nTopic: ${t.title}\nDescription: ${t.description}"
                    label to payload
                }
                "WRITTEN_NOTE" -> {
                    val n = academicDao.getWrittenNote(contextId) ?: return@withContext "" to ""
                    val label = "Note: ${n.title}"
                    val payload = "Written Note Title: ${n.title}\nContent:\n${n.content}"
                    label to payload
                }
                "AI_NOTE" -> {
                    val n = learningDao.getAiNote(contextId) ?: return@withContext "" to ""
                    val label = "AI Note: ${n.title}"
                    val payload = "AI Note Title: ${n.title}\nContent:\n${n.content}"
                    label to payload
                }
                "DOCUMENT" -> {
                    val d = academicDao.getDocument(contextId) ?: return@withContext "" to ""
                    "PDF/Doc: ${d.title} (${d.pageCount} pages)" to "Document: ${d.title} (${d.fileType}, ${d.pageCount} pages)"
                }
                else -> "" to ""
            }
        }
    }

    // ---- Storage & Backup ----
    suspend fun getStorageStats(): StorageStats = storage.computeStorageStats(StudoraDatabase.DB_NAME)

    suspend fun exportBackup(uri: Uri): Result<BackupSummary> = backupManager.exportBackupToUri(uri)

    suspend fun validateBackup(uri: Uri): Result<JSONObject> = backupManager.validateBackupUri(uri)

    suspend fun restoreBackup(uri: Uri): Result<Int> = backupManager.restoreBackupFromUri(uri)

    // ---- Date Helpers ----
    private fun computeDateTimeMillis(dateMidnightMillis: Long, minutesFromMidnight: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMidnightMillis
            set(Calendar.HOUR_OF_DAY, (minutesFromMidnight / 60).coerceIn(0, 23))
            set(Calendar.MINUTE, (minutesFromMidnight % 60).coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun computeBillReminderMillis(bill: BillEntity): Long {
        if (bill.reminderIntervalDays == 0 && bill.customReminderMillis != null) {
            return bill.customReminderMillis
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = bill.dueDateMillis
            add(Calendar.DAY_OF_YEAR, -bill.reminderIntervalDays.coerceAtLeast(0))
            set(Calendar.HOUR_OF_DAY, bill.reminderHour.coerceIn(0, 23))
            set(Calendar.MINUTE, bill.reminderMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
