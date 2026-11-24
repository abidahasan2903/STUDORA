package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StudoraApplication
import com.example.core.BackupSummary
import com.example.core.CalculatorEngine
import com.example.core.CalculatorHistoryItem
import com.example.core.CalculatorUiState
import com.example.core.DateTimeUtils
import com.example.core.PlaybackState
import com.example.core.RecordingState
import com.example.core.StorageStats
import com.example.core.TtsSynthesisProgress
import com.example.data.db.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.util.Locale

private const val TAG = "StudoraViewModel"

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Loading(val status: String) : AiTaskState
    data class Streaming(val status: String, val text: String) : AiTaskState
    data class Success(val text: String, val savedId: Long? = null) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StudoraApplication
    val repo = app.repository

    val isOnline: StateFlow<Boolean> = repo.isOnline
    val settings: StateFlow<AppSettingsEntity> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())
    val profile: StateFlow<LocalProfileEntity> = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalProfileEntity())

    // Academic & Learning flows
    val subjects: StateFlow<List<SubjectEntity>> = repo.subjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTopics: StateFlow<List<TopicEntity>> = repo.allTopics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allDocuments: StateFlow<List<DocumentEntity>> = repo.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allWrittenNotes: StateFlow<List<WrittenNoteEntity>> = repo.allWrittenNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAiNotes: StateFlow<List<AiNoteEntity>> = repo.allAiNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRecordings: StateFlow<List<AudioRecordingEntity>> = repo.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAiAudio: StateFlow<List<AiAudioEntity>> = repo.allAiAudio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allQuizzes: StateFlow<List<QuizEntity>> = repo.allQuizzes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allQuizAttempts: StateFlow<List<QuizAttemptEntity>> = repo.allQuizAttempts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFlashcards: StateFlow<List<FlashcardEntity>> = repo.allFlashcards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Productivity & Lifestyle flows
    val allTasks: StateFlow<List<TaskEntity>> = repo.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allBills: StateFlow<List<BillEntity>> = repo.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studyPlans: StateFlow<List<StudyPlanEntity>> = repo.studyPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allStudySessions: StateFlow<List<StudySessionEntity>> = repo.allStudySessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val walletTransactions: StateFlow<List<WalletTransactionEntity>> = repo.walletTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val classRoutines: StateFlow<List<ClassRoutineEntity>> = repo.classRoutines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val timetableEntries: StateFlow<List<TimetableEntryEntity>> = repo.timetableEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busSchedules: StateFlow<List<BusScheduleEntity>> = repo.busSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val foodCharts: StateFlow<List<FoodChartEntity>> = repo.foodCharts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val nutritionSuggestions: StateFlow<List<NutritionSuggestionEntity>> = repo.nutritionSuggestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContactEntity>> = repo.emergencyContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val portalEntries: StateFlow<List<PortalEntryEntity>> = repo.portalEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Studora AI flows
    val aiConversations: StateFlow<List<AiConversationEntity>> = repo.aiConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingAiRequests: StateFlow<List<PendingAiRequestEntity>> = repo.pendingAiRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Audio & Recording state
    val recordingState: StateFlow<RecordingState> = repo.audioService.recordingState
    val playbackState: StateFlow<PlaybackState> = repo.audioService.playbackState
    val ttsProgress: StateFlow<TtsSynthesisProgress> = repo.audioService.ttsProgress

    // AI generation states
    private val _aiNoteState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiNoteState: StateFlow<AiTaskState> = _aiNoteState.asStateFlow()

    private val _aiChatStreamingText = MutableStateFlow<String?>(null)
    val aiChatStreamingText: StateFlow<String?> = _aiChatStreamingText.asStateFlow()

    private val _aiChatError = MutableStateFlow<String?>(null)
    val aiChatError: StateFlow<String?> = _aiChatError.asStateFlow()

    private val _isAiChatSending = MutableStateFlow(false)
    val isAiChatSending: StateFlow<Boolean> = _isAiChatSending.asStateFlow()

    private val _quizGenState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val quizGenState: StateFlow<AiTaskState> = _quizGenState.asStateFlow()

    private val _flashcardGenState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val flashcardGenState: StateFlow<AiTaskState> = _flashcardGenState.asStateFlow()

    private val _studyPlanGenState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val studyPlanGenState: StateFlow<AiTaskState> = _studyPlanGenState.asStateFlow()

    private val _nutritionGenState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val nutritionGenState: StateFlow<AiTaskState> = _nutritionGenState.asStateFlow()

    // Storage & Backup state
    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    // Global Snackbar / Toast feedback channel
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Independent Wallet Calculator state (never alters wallet balance/transactions)
    private val _calculatorState = MutableStateFlow(CalculatorUiState())
    val calculatorState: StateFlow<CalculatorUiState> = _calculatorState.asStateFlow()
    private var calcHistorySeq = 0L

    private var activeAiJob: Job? = null

    fun notifyUser(msg: String) {
        _snackbarMessage.tryEmit(msg)
    }

    // ---- Settings & Profile ----
    fun updateTheme(mode: String) {
        viewModelScope.launch {
            repo.saveSettings(settings.value.copy(themeMode = mode))
        }
    }

    fun updateSettings(updated: AppSettingsEntity) {
        viewModelScope.launch {
            repo.saveSettings(updated)
            notifyUser("Settings saved.")
        }
    }

    fun updateProfile(updated: LocalProfileEntity) {
        viewModelScope.launch {
            repo.saveProfile(updated)
            notifyUser("Local profile updated.")
        }
    }

    // ---- Subjects & Topics ----
    fun observeSubject(id: Long) = repo.observeSubject(id)
    fun observeTopics(subjectId: Long) = repo.observeTopics(subjectId)
    fun observeDocumentsForSubject(subjectId: Long) = repo.observeDocumentsForSubject(subjectId)
    fun observeDocumentsForTopic(topicId: Long) = repo.observeDocumentsForTopic(topicId)
    fun observeWrittenNotesForSubject(subjectId: Long) = repo.observeWrittenNotesForSubject(subjectId)
    fun observeWrittenNotesForTopic(topicId: Long) = repo.observeWrittenNotesForTopic(topicId)
    fun observeQuestionsForQuiz(quizId: Long) = repo.observeQuestionsForQuiz(quizId)
    fun observeAttemptsForQuiz(quizId: Long) = repo.observeAttemptsForQuiz(quizId)
    fun observeMessages(conversationId: Long) = repo.observeMessages(conversationId)

    fun touchSubject(id: Long) {
        viewModelScope.launch { repo.touchSubject(id) }
    }

    fun saveSubject(subject: SubjectEntity, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveSubject(subject)
            notifyUser(if (subject.id == 0L) "Subject added." else "Subject updated.")
            onDone(id)
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repo.deleteSubjectAndFiles(subject)
            notifyUser("Subject and its documents removed.")
        }
    }

    fun saveTopic(topic: TopicEntity, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveTopic(topic)
            notifyUser(if (topic.id == 0L) "Topic added." else "Topic updated.")
            onDone(id)
        }
    }

    fun reorderTopic(topics: List<TopicEntity>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in topics.indices || toIndex !in topics.indices) return
        val mutable = topics.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        viewModelScope.launch { repo.reorderTopics(mutable) }
    }

    fun deleteTopic(topic: TopicEntity) {
        viewModelScope.launch {
            repo.deleteTopic(topic)
            notifyUser("Topic deleted.")
        }
    }

    // ---- Documents & Scanner ----
    fun importDocument(uri: Uri, title: String, subjectId: Long, topicId: Long?, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repo.importDocumentFromUri(uri, title, subjectId, topicId)
            res.onSuccess { doc ->
                notifyUser("Saved \"${doc.title}\" offline.")
                onResult(true)
            }.onFailure { err ->
                notifyUser(err.localizedMessage ?: "Failed to import document.")
                onResult(false)
            }
        }
    }

    fun saveScannedPdf(pageFiles: List<File>, title: String, subjectId: Long, topicId: Long?, onDone: (Result<DocumentEntity>) -> Unit) {
        viewModelScope.launch {
            val res = repo.saveScannedPdfDocument(pageFiles, title, subjectId, topicId)
            res.onSuccess {
                notifyUser("PDF Saved (${it.pageCount} pages).")
            }.onFailure {
                notifyUser(it.localizedMessage ?: "PDF export failed.")
            }
            onDone(res)
        }
    }

    fun renameDocument(doc: DocumentEntity, newTitle: String) {
        viewModelScope.launch {
            repo.renameDocument(doc, newTitle)
            notifyUser("Document renamed.")
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repo.deleteDocument(doc)
            notifyUser("Document deleted.")
        }
    }

    // ---- Written Notes ----
    fun saveWrittenNote(note: WrittenNoteEntity, silent: Boolean = false, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveWrittenNote(note)
            if (!silent) notifyUser("Note saved locally.")
            onSaved(id)
        }
    }

    fun deleteWrittenNote(note: WrittenNoteEntity) {
        viewModelScope.launch {
            repo.deleteWrittenNote(note)
            notifyUser("Note deleted.")
        }
    }

    // ---- AI Notes ----
    fun resetAiNoteState() {
        activeAiJob?.cancel()
        _aiNoteState.value = AiTaskState.Idle
    }

    fun generateAiNotes(
        document: DocumentEntity?,
        fallbackTextContext: String?,
        prompt: String
    ) {
        if (!repo.connectivityObserver.checkCurrentConnectivity()) {
            _aiNoteState.value = AiTaskState.Error("Internet connection is required to generate new AI notes.")
            return
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiNoteState.value = AiTaskState.Loading("Preparing source material...")
            var latest = ""
            try {
                val file = document?.let { File(it.filePath) }
                repo.aiService.streamNotesFromDocument(
                    documentFile = file,
                    fileType = document?.fileType ?: "TEXT",
                    userPrompt = prompt,
                    fallbackTextContext = fallbackTextContext,
                    onStatusUpdate = { status ->
                        _aiNoteState.value = if (latest.isNotBlank()) {
                            AiTaskState.Streaming(status, latest)
                        } else {
                            AiTaskState.Loading(status)
                        }
                    }
                ).collect { accumulated ->
                    latest = accumulated
                    _aiNoteState.value = AiTaskState.Streaming("Generating AI notes...", accumulated)
                }
                if (latest.isNotBlank()) {
                    _aiNoteState.value = AiTaskState.Success(latest)
                } else {
                    _aiNoteState.value = AiTaskState.Error("AI returned an empty note.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI note generation error", e)
                _aiNoteState.value = AiTaskState.Error(e.localizedMessage ?: "AI note generation failed.")
            }
        }
    }

    fun saveGeneratedAiNote(
        existingId: Long = 0L,
        title: String,
        prompt: String,
        content: String,
        subjectId: Long?,
        topicId: Long?,
        documentId: Long?,
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repo.saveAiNote(
                AiNoteEntity(
                    id = existingId,
                    subjectId = subjectId,
                    topicId = topicId,
                    documentId = documentId,
                    title = title.trim().ifBlank { "AI Study Notes" },
                    prompt = prompt,
                    content = content
                )
            )
            notifyUser("AI Note saved for offline reading.")
            onSaved(id)
        }
    }

    fun deleteAiNote(note: AiNoteEntity) {
        viewModelScope.launch {
            repo.deleteAiNote(note)
            notifyUser("AI Note deleted.")
        }
    }

    // ---- Voice Notes & AI Audio ----
    fun startMicRecording() {
        val res = repo.audioService.startRecording()
        res.onFailure { notifyUser(it.localizedMessage ?: "Unable to start microphone.") }
    }

    fun pauseMicRecording() = repo.audioService.pauseRecording()
    fun resumeMicRecording() = repo.audioService.resumeRecording()
    fun cancelMicRecording() = repo.audioService.cancelRecording()

    fun stopAndSaveMicRecording(
        title: String,
        subjectId: Long?,
        topicId: Long?,
        documentId: Long?,
        onSaved: (Boolean) -> Unit = {}
    ) {
        val res = repo.audioService.stopRecording()
        res.onSuccess { (file, durationMs) ->
            viewModelScope.launch {
                repo.saveRecording(
                    AudioRecordingEntity(
                        subjectId = subjectId,
                        topicId = topicId,
                        documentId = documentId,
                        title = title.trim().ifBlank { "Voice Note ${DateTimeUtils.formatShortDate(System.currentTimeMillis())}" },
                        filePath = file.absolutePath,
                        durationMs = durationMs
                    )
                )
                notifyUser("Voice recording saved locally.")
                onSaved(true)
            }
        }.onFailure {
            notifyUser(it.localizedMessage ?: "Failed to save recording.")
            onSaved(false)
        }
    }

    fun renameRecording(rec: AudioRecordingEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repo.saveRecording(rec.copy(title = newTitle.trim()))
            notifyUser("Recording renamed.")
        }
    }

    fun deleteRecording(rec: AudioRecordingEntity) {
        if (playbackState.value.playingFilePath == rec.filePath) repo.audioService.stopPlayback()
        viewModelScope.launch {
            repo.deleteRecording(rec)
            notifyUser("Voice recording deleted.")
        }
    }

    fun playAudio(filePath: String, title: String) {
        repo.audioService.playFile(filePath, title, settings.value.ttsSpeechRate)
    }

    fun toggleAudioPlayPause() = repo.audioService.togglePlayPause()
    fun seekAudio(posMs: Int) = repo.audioService.seekTo(posMs)
    fun setAudioSpeed(speed: Float) = repo.audioService.setPlaybackSpeed(speed)
    fun stopAudio() = repo.audioService.stopPlayback()

    suspend fun getTtsLanguages(): List<Locale> = repo.audioService.getAvailableTtsLanguages()

    fun generateAiAudio(
        title: String,
        text: String,
        sourceType: String,
        sourceId: Long?,
        subjectId: Long?,
        topicId: Long?,
        languageTag: String,
        speechRate: Float,
        onComplete: (Result<AiAudioEntity>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repo.generateAndSaveAiAudio(
                title = title,
                text = text,
                sourceType = sourceType,
                sourceId = sourceId,
                subjectId = subjectId,
                topicId = topicId,
                languageTag = languageTag,
                speechRate = speechRate
            )
            res.onSuccess {
                notifyUser("Audio Generated and saved for offline playback.")
            }.onFailure {
                notifyUser(it.localizedMessage ?: "Audio generation failed.")
            }
            onComplete(res)
        }
    }

    fun deleteAiAudio(audio: AiAudioEntity) {
        if (playbackState.value.playingFilePath == audio.filePath) repo.audioService.stopPlayback()
        viewModelScope.launch {
            repo.deleteAiAudio(audio)
            notifyUser("AI Audio deleted.")
        }
    }

    // ---- AI Quiz & Flashcards ----
    fun resetQuizGenState() { _quizGenState.value = AiTaskState.Idle }
    fun resetFlashcardGenState() { _flashcardGenState.value = AiTaskState.Idle }

    fun generateQuiz(
        subjectId: Long?,
        topicId: Long?,
        documentId: Long?,
        sourceText: String?,
        questionType: String,
        questionCount: Int,
        difficulty: String,
        onCreated: (Long) -> Unit = {}
    ) {
        if (!repo.connectivityObserver.checkCurrentConnectivity()) {
            _quizGenState.value = AiTaskState.Error("Internet is required to generate a new AI quiz.")
            return
        }
        viewModelScope.launch {
            _quizGenState.value = AiTaskState.Loading("Generating $questionCount $questionType questions...")
            val res = repo.generateAndSaveQuiz(subjectId, topicId, documentId, sourceText, questionType, questionCount, difficulty)
            res.onSuccess { quizId ->
                _quizGenState.value = AiTaskState.Success("Quiz ready!", savedId = quizId)
                notifyUser("Quiz generated and saved offline.")
                onCreated(quizId)
            }.onFailure { e ->
                _quizGenState.value = AiTaskState.Error(e.localizedMessage ?: "Quiz generation failed.")
            }
        }
    }

    fun saveQuizAttempt(quizId: Long, score: Int, total: Int, answers: Map<Long, String>) {
        viewModelScope.launch {
            repo.saveQuizAttempt(quizId, score, total, answers)
        }
    }

    fun deleteQuiz(quiz: QuizEntity) {
        viewModelScope.launch {
            repo.deleteQuiz(quiz)
            notifyUser("Quiz deleted.")
        }
    }

    fun generateFlashcards(
        subjectId: Long?,
        topicId: Long?,
        documentId: Long?,
        sourceText: String?,
        count: Int
    ) {
        if (!repo.connectivityObserver.checkCurrentConnectivity()) {
            _flashcardGenState.value = AiTaskState.Error("Internet is required to generate AI flashcards.")
            return
        }
        viewModelScope.launch {
            _flashcardGenState.value = AiTaskState.Loading("Generating $count flashcards...")
            val res = repo.generateAndSaveFlashcards(subjectId, topicId, documentId, sourceText, count)
            res.onSuccess { total ->
                _flashcardGenState.value = AiTaskState.Success("Created $total flashcards!")
                notifyUser("Saved $total flashcards offline.")
            }.onFailure { e ->
                _flashcardGenState.value = AiTaskState.Error(e.localizedMessage ?: "Flashcard generation failed.")
            }
        }
    }

    fun saveFlashcard(card: FlashcardEntity) {
        viewModelScope.launch {
            repo.saveFlashcard(card)
            notifyUser("Flashcard saved.")
        }
    }

    fun reviewFlashcard(card: FlashcardEntity, masteryStatus: String) {
        viewModelScope.launch { repo.updateFlashcardReview(card, masteryStatus) }
    }

    fun deleteFlashcard(card: FlashcardEntity) {
        viewModelScope.launch {
            repo.deleteFlashcard(card)
            notifyUser("Flashcard deleted.")
        }
    }

    // ---- Tasks & Bills ----
    fun saveTask(task: TaskEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val (_, sched) = repo.saveTaskWithNotification(task)
            val msg = when {
                task.isReminderEnabled && sched != null && sched.scheduled ->
                    "Task saved & reminder set."
                task.isReminderEnabled && sched != null && !sched.scheduled ->
                    "Task saved. ${sched.message}"
                else -> "Task saved."
            }
            notifyUser(msg)
            onDone()
        }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch { repo.toggleTaskComplete(task) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repo.deleteTask(task)
            notifyUser("Task deleted.")
        }
    }

    fun saveBill(bill: BillEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val (_, sched) = repo.saveBillWithNotification(bill)
            val msg = if (bill.status == "PENDING" && sched != null) {
                if (sched.scheduled) "Bill saved & alert set." else "Bill saved. ${sched.message}"
            } else {
                "Bill saved."
            }
            notifyUser(msg)
            onDone()
        }
    }

    fun toggleBillPaid(bill: BillEntity) {
        viewModelScope.launch {
            repo.toggleBillPaid(bill)
            notifyUser(if (bill.status == "PAID") "Bill reopened as Pending." else "Bill marked as Paid.")
        }
    }

    fun deleteBill(bill: BillEntity) {
        viewModelScope.launch {
            repo.deleteBill(bill)
            notifyUser("Bill deleted.")
        }
    }

    // ---- Self Study Routine ----
    fun resetStudyPlanGenState() { _studyPlanGenState.value = AiTaskState.Idle }

    fun generateStudyPlan(
        subjects: String,
        academicLevel: String,
        examDateMillis: Long?,
        examInfo: String,
        dailyMinutes: Int,
        preferredHours: String,
        goals: String,
        difficultSubjects: String,
        breakPreference: String,
        weeklyAvailability: String,
        onCreated: (Long) -> Unit = {}
    ) {
        if (!repo.connectivityObserver.checkCurrentConnectivity()) {
            _studyPlanGenState.value = AiTaskState.Error("Internet is required for AI study planning. You can create a manual study plan offline.")
            return
        }
        viewModelScope.launch {
            _studyPlanGenState.value = AiTaskState.Loading("Building your personalized study routine...")
            val res = repo.generateAndSaveAiStudyPlan(
                subjects, academicLevel, examDateMillis, examInfo,
                dailyMinutes, preferredHours, goals, difficultSubjects, breakPreference, weeklyAvailability
            )
            res.onSuccess { id ->
                _studyPlanGenState.value = AiTaskState.Success("Study routine created!", savedId = id)
                notifyUser("Study plan saved offline.")
                onCreated(id)
            }.onFailure { e ->
                _studyPlanGenState.value = AiTaskState.Error(e.localizedMessage ?: "Failed to generate study plan.")
            }
        }
    }

    fun createManualStudyPlan(title: String, subjectsSummary: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.createManualStudyPlan(title, subjectsSummary)
            notifyUser("Manual study plan created.")
            onCreated(id)
        }
    }

    fun saveStudySession(session: StudySessionEntity) {
        viewModelScope.launch {
            repo.saveStudySession(session)
            notifyUser("Study session saved.")
        }
    }

    fun toggleStudySessionComplete(session: StudySessionEntity) {
        viewModelScope.launch { repo.toggleStudySessionComplete(session) }
    }

    fun deleteStudySession(session: StudySessionEntity) {
        viewModelScope.launch { repo.deleteStudySession(session) }
    }

    fun deleteStudyPlan(plan: StudyPlanEntity) {
        viewModelScope.launch {
            repo.deleteStudyPlan(plan)
            notifyUser("Study plan deleted.")
        }
    }

    // ---- Wallet ----
    fun saveWalletTransaction(tx: WalletTransactionEntity) {
        viewModelScope.launch {
            repo.saveWalletTransaction(tx)
            notifyUser(if (tx.id == 0L) "Transaction recorded." else "Transaction updated.")
        }
    }

    fun deleteWalletTransaction(tx: WalletTransactionEntity) {
        viewModelScope.launch {
            repo.deleteWalletTransaction(tx)
            notifyUser("Transaction deleted.")
        }
    }

    // ---- Wallet Calculator (Standalone, Independent) ----
    private fun refreshLivePreview(expression: String, currentInput: String, awaitingNext: Boolean): String {
        if (awaitingNext || expression.isBlank()) return ""
        val combined = "$expression$currentInput"
        return CalculatorEngine.evaluate(combined).fold(
            onSuccess = { CalculatorEngine.formatDecimal(it) },
            onFailure = { "" }
        )
    }

    fun onCalcDigit(digit: String) {
        _calculatorState.update { st ->
            if (st.justEvaluated || st.errorMessage != null) {
                st.copy(
                    expression = "",
                    currentInput = digit,
                    resultDisplay = "",
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = false
                )
            } else if (st.awaitingNextOperand) {
                st.copy(
                    currentInput = digit,
                    resultDisplay = refreshLivePreview(st.expression, digit, false),
                    errorMessage = null,
                    awaitingNextOperand = false
                )
            } else {
                val base = if (st.currentInput.endsWith("%")) "${st.currentInput}×" else st.currentInput
                val nextInput = when {
                    base == "0" -> digit
                    base == "-0" -> "-$digit"
                    base.endsWith("×") -> {
                        // Started typing after %
                        return@update st.copy(
                            expression = "${st.expression}$base ",
                            currentInput = digit,
                            resultDisplay = refreshLivePreview("${st.expression}$base ", digit, false),
                            errorMessage = null,
                            awaitingNextOperand = false
                        )
                    }
                    else -> (base + digit).take(24)
                }
                st.copy(
                    currentInput = nextInput,
                    resultDisplay = refreshLivePreview(st.expression, nextInput, false),
                    errorMessage = null
                )
            }
        }
    }

    fun onCalcDecimal() {
        _calculatorState.update { st ->
            if (st.justEvaluated || st.errorMessage != null) {
                st.copy(
                    expression = "",
                    currentInput = "0.",
                    resultDisplay = "",
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = false
                )
            } else if (st.awaitingNextOperand) {
                st.copy(
                    currentInput = "0.",
                    resultDisplay = refreshLivePreview(st.expression, "0.", false),
                    errorMessage = null,
                    awaitingNextOperand = false
                )
            } else if (st.currentInput.contains(".") || st.currentInput.endsWith("%")) {
                st
            } else {
                val nextInput = "${st.currentInput}."
                st.copy(
                    currentInput = nextInput,
                    resultDisplay = refreshLivePreview(st.expression, nextInput, false),
                    errorMessage = null
                )
            }
        }
    }

    fun onCalcOperator(opSymbol: String) {
        _calculatorState.update { st ->
            if (st.errorMessage != null) {
                val seed = st.lastResult?.let { CalculatorEngine.formatDecimal(it) } ?: "0"
                return@update st.copy(
                    expression = "$seed $opSymbol ",
                    currentInput = seed,
                    resultDisplay = "",
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = true
                )
            }
            if (st.justEvaluated) {
                val seed = st.lastResult?.let { CalculatorEngine.formatDecimal(it) } ?: st.currentInput
                return@update st.copy(
                    expression = "$seed $opSymbol ",
                    currentInput = seed,
                    resultDisplay = "",
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = true
                )
            }
            if (st.awaitingNextOperand && st.expression.length >= 3) {
                val replaced = st.expression.dropLast(3) + " $opSymbol "
                return@update st.copy(
                    expression = replaced,
                    errorMessage = null
                )
            }
            val cleanOperand = st.currentInput.trimEnd('.')
            val nextExpr = "${st.expression}$cleanOperand $opSymbol "
            st.copy(
                expression = nextExpr,
                currentInput = cleanOperand,
                resultDisplay = refreshLivePreview(st.expression, cleanOperand, false),
                errorMessage = null,
                awaitingNextOperand = true
            )
        }
    }

    fun onCalcPercent() {
        _calculatorState.update { st ->
            if (st.errorMessage != null) return@update st
            if (st.justEvaluated) {
                val seed = st.lastResult?.let { CalculatorEngine.formatDecimal(it) } ?: st.currentInput
                val nextInput = "$seed%"
                val eval = CalculatorEngine.evaluate(nextInput)
                return@update eval.fold(
                    onSuccess = { v ->
                        val formatted = CalculatorEngine.formatDecimal(v)
                        st.copy(
                            expression = "",
                            currentInput = formatted,
                            resultDisplay = formatted,
                            lastResult = v,
                            justEvaluated = false,
                            awaitingNextOperand = false
                        )
                    },
                    onFailure = { st }
                )
            }
            if (st.awaitingNextOperand || st.currentInput.endsWith("%")) {
                st
            } else {
                val nextInput = "${st.currentInput.trimEnd('.')}%"
                st.copy(
                    currentInput = nextInput,
                    resultDisplay = refreshLivePreview(st.expression, nextInput, false),
                    errorMessage = null
                )
            }
        }
    }

    fun onCalcToggleSign() {
        _calculatorState.update { st ->
            if (st.errorMessage != null) return@update st
            if (st.justEvaluated) {
                val cur = st.lastResult ?: return@update st
                if (cur.compareTo(BigDecimal.ZERO) == 0) return@update st
                val negated = cur.negate()
                val formatted = CalculatorEngine.formatDecimal(negated)
                return@update st.copy(
                    expression = "",
                    currentInput = formatted,
                    resultDisplay = "",
                    lastResult = negated,
                    justEvaluated = false,
                    awaitingNextOperand = false
                )
            }
            if (st.awaitingNextOperand) return@update st
            val cur = st.currentInput
            if (cur == "0" || cur == "0.") return@update st
            val nextInput = if (cur.startsWith("-")) cur.drop(1) else "-$cur"
            st.copy(
                currentInput = nextInput,
                resultDisplay = refreshLivePreview(st.expression, nextInput, false),
                errorMessage = null
            )
        }
    }

    fun onCalcBackspace() {
        _calculatorState.update { st ->
            if (st.justEvaluated || st.errorMessage != null) {
                return@update st.copy(
                    expression = "",
                    currentInput = "0",
                    resultDisplay = "",
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = false
                )
            }
            if (st.awaitingNextOperand) {
                // Remove trailing operator and restore last operand into currentInput
                val trimmed = st.expression.trimEnd()
                val withoutOp = trimmed.dropLast(1).trimEnd()
                if (withoutOp.isBlank()) {
                    return@update st.copy(expression = "", currentInput = "0", awaitingNextOperand = false, resultDisplay = "")
                }
                val lastSpace = withoutOp.lastIndexOf(' ')
                val restoredOperand = if (lastSpace >= 0) withoutOp.substring(lastSpace + 1) else withoutOp
                val remainingExpr = if (lastSpace >= 0) withoutOp.substring(0, lastSpace + 1) else ""
                return@update st.copy(
                    expression = remainingExpr,
                    currentInput = restoredOperand,
                    awaitingNextOperand = false,
                    resultDisplay = refreshLivePreview(remainingExpr, restoredOperand, false)
                )
            }
            val nextInput = when {
                st.currentInput.length <= 1 -> "0"
                st.currentInput.length == 2 && st.currentInput.startsWith("-") -> "0"
                else -> st.currentInput.dropLast(1)
            }
            st.copy(
                currentInput = nextInput,
                resultDisplay = refreshLivePreview(st.expression, nextInput, false),
                errorMessage = null
            )
        }
    }

    fun onCalcClearEntry() {
        _calculatorState.update { st ->
            if (st.justEvaluated || st.errorMessage != null) {
                st.copy(
                    expression = "",
                    currentInput = "0",
                    resultDisplay = "",
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = false
                )
            } else {
                st.copy(
                    currentInput = "0",
                    resultDisplay = refreshLivePreview(st.expression, "0", st.awaitingNextOperand),
                    errorMessage = null,
                    awaitingNextOperand = false
                )
            }
        }
    }

    fun onCalcClearAll() {
        _calculatorState.update { st ->
            CalculatorUiState(history = st.history)
        }
    }

    fun onCalcEquals() {
        _calculatorState.update { st ->
            if (st.justEvaluated) return@update st
            val rawFull = if (st.awaitingNextOperand && st.expression.length >= 3) {
                st.expression.dropLast(3)
            } else {
                "${st.expression}${st.currentInput.trimEnd('.')}"
            }
            if (rawFull.isBlank()) return@update st

            CalculatorEngine.evaluate(rawFull).fold(
                onSuccess = { value ->
                    val formatted = CalculatorEngine.formatDecimal(value)
                    val entry = CalculatorHistoryItem(
                        id = ++calcHistorySeq,
                        expression = rawFull.trim(),
                        result = formatted,
                        numericValue = value
                    )
                    st.copy(
                        expression = "${rawFull.trim()} =",
                        currentInput = formatted,
                        resultDisplay = formatted,
                        errorMessage = null,
                        justEvaluated = true,
                        awaitingNextOperand = false,
                        lastResult = value,
                        history = (listOf(entry) + st.history).take(30)
                    )
                },
                onFailure = { err ->
                    val msg = if (err is ArithmeticException) {
                        "Cannot divide by zero"
                    } else {
                        "Invalid expression"
                    }
                    st.copy(
                        errorMessage = msg,
                        resultDisplay = "",
                        justEvaluated = false
                    )
                }
            )
        }
    }

    fun onCalcReuseHistory(item: CalculatorHistoryItem) {
        _calculatorState.update { st ->
            if (st.awaitingNextOperand && st.expression.isNotBlank()) {
                st.copy(
                    currentInput = item.result,
                    resultDisplay = refreshLivePreview(st.expression, item.result, false),
                    errorMessage = null,
                    justEvaluated = false,
                    awaitingNextOperand = false
                )
            } else {
                st.copy(
                    expression = "",
                    currentInput = item.result,
                    resultDisplay = item.result,
                    errorMessage = null,
                    justEvaluated = true,
                    awaitingNextOperand = false,
                    lastResult = item.numericValue
                )
            }
        }
    }

    fun onCalcClearHistory() {
        _calculatorState.update { it.copy(history = emptyList()) }
    }

    // ---- Class Routine & Bus Schedule ----
    fun importRoutineUri(uri: Uri, title: String) {
        viewModelScope.launch {
            val mime = repo.storage.getMimeType(uri)
            val isPdf = mime.contains("pdf", true)
            val copied = repo.storage.copyUriToDirectory(uri, repo.storage.routinesDir, if (isPdf) "pdf" else null)
            copied.onSuccess { file ->
                val pages = if (isPdf) com.example.core.PdfUtils.getPdfPageCount(file).getOrDefault(1) else 1
                repo.saveClassRoutineFile(title.ifBlank { "Class Routine" }, file, if (isPdf) "PDF" else "IMAGE", pages)
                notifyUser("Routine saved offline.")
            }.onFailure { notifyUser(it.localizedMessage ?: "Import failed.") }
        }
    }

    fun saveScannedRoutinePdf(pages: List<File>, title: String) {
        viewModelScope.launch {
            val pdfFile = repo.storage.createNewFile(repo.storage.routinesDir, "routine", "pdf")
            com.example.core.PdfUtils.createPdfFromImages(pages, pdfFile).onSuccess { count ->
                repo.saveClassRoutineFile(title.ifBlank { "Scanned Routine" }, pdfFile, "PDF", count)
                notifyUser("Routine PDF saved ($count pages).")
            }.onFailure { notifyUser(it.localizedMessage ?: "Failed to save PDF.") }
        }
    }

    fun renameRoutine(routine: ClassRoutineEntity, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.updateClassRoutine(routine.copy(title = title.trim()))
            notifyUser("Routine renamed.")
        }
    }

    fun deleteRoutine(routine: ClassRoutineEntity) {
        viewModelScope.launch {
            repo.deleteClassRoutine(routine)
            notifyUser("Routine deleted.")
        }
    }

    fun saveTimetableEntry(entry: TimetableEntryEntity) {
        viewModelScope.launch {
            repo.saveTimetableEntry(entry)
            notifyUser("Timetable entry saved.")
        }
    }

    fun deleteTimetableEntry(entry: TimetableEntryEntity) {
        viewModelScope.launch { repo.deleteTimetableEntry(entry) }
    }

    fun importBusScheduleUri(uri: Uri, title: String) {
        viewModelScope.launch {
            val mime = repo.storage.getMimeType(uri)
            val isPdf = mime.contains("pdf", true)
            repo.storage.copyUriToDirectory(uri, repo.storage.busDir, if (isPdf) "pdf" else null).onSuccess { file ->
                val pages = if (isPdf) com.example.core.PdfUtils.getPdfPageCount(file).getOrDefault(1) else 1
                repo.saveBusSchedule(
                    BusScheduleEntity(
                        isDocumentRecord = true,
                        title = title.ifBlank { "Bus Schedule" },
                        filePath = file.absolutePath,
                        fileType = if (isPdf) "PDF" else "IMAGE",
                        pageCount = pages
                    )
                )
                notifyUser("Bus schedule saved offline.")
            }.onFailure { notifyUser(it.localizedMessage ?: "Import failed.") }
        }
    }

    fun saveScannedBusPdf(pages: List<File>, title: String) {
        viewModelScope.launch {
            val pdfFile = repo.storage.createNewFile(repo.storage.busDir, "bus", "pdf")
            com.example.core.PdfUtils.createPdfFromImages(pages, pdfFile).onSuccess { count ->
                repo.saveBusSchedule(
                    BusScheduleEntity(
                        isDocumentRecord = true,
                        title = title.ifBlank { "Scanned Bus Schedule" },
                        filePath = pdfFile.absolutePath,
                        fileType = "PDF",
                        pageCount = count
                    )
                )
                notifyUser("Bus schedule PDF saved.")
            }.onFailure { notifyUser(it.localizedMessage ?: "Failed to create PDF.") }
        }
    }

    fun saveBusSchedule(schedule: BusScheduleEntity) {
        viewModelScope.launch {
            repo.saveBusSchedule(schedule)
            notifyUser("Bus schedule saved.")
        }
    }

    fun deleteBusSchedule(schedule: BusScheduleEntity) {
        viewModelScope.launch {
            repo.deleteBusSchedule(schedule)
            notifyUser("Bus schedule deleted.")
        }
    }

    // ---- Nutrition Guide ----
    fun resetNutritionGenState() { _nutritionGenState.value = AiTaskState.Idle }

    fun importFoodChartUri(uri: Uri, title: String) {
        viewModelScope.launch {
            val mime = repo.storage.getMimeType(uri)
            val isPdf = mime.contains("pdf", true)
            repo.storage.copyUriToDirectory(uri, repo.storage.nutritionDir, if (isPdf) "pdf" else null).onSuccess { file ->
                val pages = if (isPdf) com.example.core.PdfUtils.getPdfPageCount(file).getOrDefault(1) else 1
                repo.saveFoodChart(title.ifBlank { "Food Chart" }, file, if (isPdf) "PDF" else "IMAGE", pages)
                notifyUser("Food chart saved offline.")
            }.onFailure { notifyUser(it.localizedMessage ?: "Import failed.") }
        }
    }

    fun saveScannedFoodChartPdf(pages: List<File>, title: String) {
        viewModelScope.launch {
            val pdfFile = repo.storage.createNewFile(repo.storage.nutritionDir, "nutrition", "pdf")
            com.example.core.PdfUtils.createPdfFromImages(pages, pdfFile).onSuccess { count ->
                repo.saveFoodChart(title.ifBlank { "Scanned Food Chart" }, pdfFile, "PDF", count)
                notifyUser("Food chart PDF saved.")
            }.onFailure { notifyUser(it.localizedMessage ?: "PDF creation failed.") }
        }
    }

    fun deleteFoodChart(chart: FoodChartEntity) {
        viewModelScope.launch {
            repo.deleteFoodChart(chart)
            notifyUser("Food chart deleted.")
        }
    }

    fun analyzeFoodChart(chart: FoodChartEntity, prompt: String) {
        if (!repo.connectivityObserver.checkCurrentConnectivity()) {
            _nutritionGenState.value = AiTaskState.Error("Internet is required for AI food chart analysis.")
            return
        }
        viewModelScope.launch {
            _nutritionGenState.value = AiTaskState.Loading("Analyzing food chart...")
            repo.analyzeAndSaveFoodChart(chart, prompt).onSuccess {
                _nutritionGenState.value = AiTaskState.Success("Analysis saved offline!", savedId = it.id)
                notifyUser("Nutrition suggestions saved offline.")
            }.onFailure { e ->
                _nutritionGenState.value = AiTaskState.Error(e.localizedMessage ?: "Analysis failed.")
            }
        }
    }

    fun deleteNutritionSuggestion(s: NutritionSuggestionEntity) {
        viewModelScope.launch { repo.deleteNutritionSuggestion(s) }
    }

    // ---- Emergency Contacts ----
    fun saveEmergencyContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            repo.saveEmergencyContact(contact)
            notifyUser("Emergency contact saved.")
        }
    }

    fun deleteEmergencyContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            repo.deleteEmergencyContact(contact)
            notifyUser("Contact deleted.")
        }
    }

    // ---- My Portal ----
    fun savePortalEntry(entry: PortalEntryEntity, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val res = repo.savePortalEntry(entry)
            res.onSuccess { notifyUser("Portal saved.") }
            onResult(res)
        }
    }

    fun markPortalOpened(entry: PortalEntryEntity) {
        viewModelScope.launch { repo.markPortalOpened(entry) }
    }

    fun reorderPortals(entries: List<PortalEntryEntity>, from: Int, to: Int) {
        if (from !in entries.indices || to !in entries.indices) return
        val list = entries.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        viewModelScope.launch { repo.reorderPortals(list) }
    }

    fun deletePortalEntry(entry: PortalEntryEntity) {
        viewModelScope.launch {
            repo.deletePortalEntry(entry)
            notifyUser("Portal deleted.")
        }
    }

    // ---- Studora AI Chat ----
    fun createAiConversation(
        title: String = "New AI Conversation",
        contextType: String? = null,
        contextId: Long? = null,
        contextLabel: String? = null,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repo.createConversation(title, contextType, contextId, contextLabel)
            onCreated(id)
        }
    }

    fun setConversationContext(convId: Long, contextType: String?, contextId: Long?, contextLabel: String?) {
        viewModelScope.launch {
            repo.updateConversationContext(convId, contextType, contextId, contextLabel)
        }
    }

    fun deleteAiConversation(conv: AiConversationEntity) {
        viewModelScope.launch { repo.deleteConversation(conv) }
    }

    fun clearAiChatError() { _aiChatError.value = null }

    fun sendAiChatMessage(
        conversationId: Long,
        userText: String,
        contextType: String? = null,
        contextId: Long? = null,
        alreadyRecordedUserMessage: Boolean = false
    ) {
        val trimmed = userText.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _aiChatError.value = null
            val (ctxLabel, ctxPayload) = if (contextType != null && contextId != null) {
                repo.buildExplicitContextText(contextType, contextId)
            } else "" to ""

            if (!alreadyRecordedUserMessage) {
                repo.insertMessage(
                    AiMessageEntity(
                        conversationId = conversationId,
                        role = "USER",
                        content = trimmed,
                        contextSummary = ctxLabel.ifBlank { null }
                    )
                )
            }

            if (!repo.connectivityObserver.checkCurrentConnectivity()) {
                repo.queuePendingAiRequest(conversationId, trimmed, ctxLabel, ctxPayload)
                _aiChatError.value = "You are offline. Request saved to your Offline Queue so you can retry when connected."
                return@launch
            }

            _isAiChatSending.value = true
            _aiChatStreamingText.value = ""
            try {
                val history = repo.getMessagesSync(conversationId)
                    .filter { it.role == "USER" || it.role == "ASSISTANT" }
                    .dropLast(1)
                    .map { it.role to it.content }

                var finalReply = ""
                repo.aiService.streamChatMessage(
                    historyMessages = history,
                    userMessage = trimmed,
                    explicitContext = ctxPayload.ifBlank { null }
                ).collect { chunk ->
                    finalReply = chunk
                    _aiChatStreamingText.value = chunk
                }
                if (finalReply.isNotBlank()) {
                    repo.insertMessage(
                        AiMessageEntity(
                            conversationId = conversationId,
                            role = "ASSISTANT",
                            content = finalReply
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Studora AI chat failed", e)
                _aiChatError.value = e.localizedMessage ?: "AI request failed. Please retry."
            } finally {
                _aiChatStreamingText.value = null
                _isAiChatSending.value = false
            }
        }
    }

    fun retryPendingRequest(req: PendingAiRequestEntity) {
        if (!repo.connectivityObserver.checkCurrentConnectivity()) {
            notifyUser("Still offline. Connect to the internet to send queued requests.")
            return
        }
        viewModelScope.launch {
            val convId = req.conversationId ?: repo.createConversation(req.prompt.take(40))
            repo.deletePendingAiRequest(req)
            sendAiChatMessage(
                conversationId = convId,
                userText = req.prompt,
                alreadyRecordedUserMessage = req.conversationId != null
            )
        }
    }

    fun deletePendingRequest(req: PendingAiRequestEntity) {
        viewModelScope.launch { repo.deletePendingAiRequest(req) }
    }

    // ---- Storage & Backup ----
    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repo.getStorageStats()
        }
    }

    fun exportBackup(uri: Uri, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            _backupBusy.value = true
            val res = repo.exportBackup(uri)
            _backupBusy.value = false
            res.onSuccess {
                notifyUser("Backup exported (${it.filesCount} items).")
            }.onFailure {
                notifyUser(it.localizedMessage ?: "Backup export failed.")
            }
            onResult(res)
        }
    }

    fun restoreBackup(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            _backupBusy.value = true
            val res = repo.restoreBackup(uri)
            _backupBusy.value = false
            res.onSuccess {
                notifyUser("Backup restored ($it files). Restarting app state...")
            }.onFailure {
                notifyUser(it.localizedMessage ?: "Backup restore failed.")
            }
            onResult(res)
        }
    }
}
