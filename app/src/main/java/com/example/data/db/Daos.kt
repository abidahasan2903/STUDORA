package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM local_profile WHERE id = 1")
    fun observeProfile(): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_profile WHERE id = 1")
    suspend fun getProfile(): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)
}

@Dao
interface AcademicDao {
    @Query("SELECT * FROM subjects ORDER BY lastAccessedAt DESC, name ASC")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun observeSubject(id: Long): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubject(id: Long): SubjectEntity?

    @Insert
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Query("UPDATE subjects SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun touchSubject(id: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("SELECT * FROM topics WHERE subjectId = :subjectId ORDER BY displayOrder ASC, createdAt ASC")
    fun observeTopicsForSubject(subjectId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY displayOrder ASC, createdAt ASC")
    fun observeAllTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getTopic(id: Long): TopicEntity?

    @Insert
    suspend fun insertTopic(topic: TopicEntity): Long

    @Update
    suspend fun updateTopic(topic: TopicEntity)

    @Update
    suspend fun updateTopics(topics: List<TopicEntity>)

    @Delete
    suspend fun deleteTopic(topic: TopicEntity)

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun observeDocumentsForSubject(subjectId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE topicId = :topicId ORDER BY updatedAt DESC")
    fun observeDocumentsForTopic(topicId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE subjectId = :subjectId")
    suspend fun getDocumentsBySubjectSync(subjectId: Long): List<DocumentEntity>

    @Insert
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Delete
    suspend fun deleteDocument(doc: DocumentEntity)

    @Query("SELECT * FROM written_notes ORDER BY updatedAt DESC")
    fun observeAllWrittenNotes(): Flow<List<WrittenNoteEntity>>

    @Query("SELECT * FROM written_notes WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun observeWrittenNotesForSubject(subjectId: Long): Flow<List<WrittenNoteEntity>>

    @Query("SELECT * FROM written_notes WHERE topicId = :topicId ORDER BY updatedAt DESC")
    fun observeWrittenNotesForTopic(topicId: Long): Flow<List<WrittenNoteEntity>>

    @Query("SELECT * FROM written_notes WHERE id = :id")
    suspend fun getWrittenNote(id: Long): WrittenNoteEntity?

    @Insert
    suspend fun insertWrittenNote(note: WrittenNoteEntity): Long

    @Update
    suspend fun updateWrittenNote(note: WrittenNoteEntity)

    @Delete
    suspend fun deleteWrittenNote(note: WrittenNoteEntity)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM ai_notes ORDER BY updatedAt DESC")
    fun observeAllAiNotes(): Flow<List<AiNoteEntity>>

    @Query("SELECT * FROM ai_notes WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun observeAiNotesForSubject(subjectId: Long): Flow<List<AiNoteEntity>>

    @Query("SELECT * FROM ai_notes WHERE id = :id")
    suspend fun getAiNote(id: Long): AiNoteEntity?

    @Insert
    suspend fun insertAiNote(note: AiNoteEntity): Long

    @Update
    suspend fun updateAiNote(note: AiNoteEntity)

    @Delete
    suspend fun deleteAiNote(note: AiNoteEntity)

    @Query("SELECT * FROM audio_recordings ORDER BY updatedAt DESC")
    fun observeAllRecordings(): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM audio_recordings WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun observeRecordingsForSubject(subjectId: Long): Flow<List<AudioRecordingEntity>>

    @Insert
    suspend fun insertRecording(rec: AudioRecordingEntity): Long

    @Update
    suspend fun updateRecording(rec: AudioRecordingEntity)

    @Delete
    suspend fun deleteRecording(rec: AudioRecordingEntity)

    @Query("SELECT * FROM ai_audio ORDER BY updatedAt DESC")
    fun observeAllAiAudio(): Flow<List<AiAudioEntity>>

    @Insert
    suspend fun insertAiAudio(audio: AiAudioEntity): Long

    @Update
    suspend fun updateAiAudio(audio: AiAudioEntity)

    @Delete
    suspend fun deleteAiAudio(audio: AiAudioEntity)

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun observeAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :id")
    suspend fun getQuiz(id: Long): QuizEntity?

    @Insert
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Delete
    suspend fun deleteQuiz(quiz: QuizEntity)

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId ORDER BY orderIndex ASC")
    fun observeQuestionsForQuiz(quizId: Long): Flow<List<QuizQuestionEntity>>

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId ORDER BY orderIndex ASC")
    suspend fun getQuestionsForQuizSync(quizId: Long): List<QuizQuestionEntity>

    @Insert
    suspend fun insertQuizQuestions(questions: List<QuizQuestionEntity>)

    @Query("SELECT * FROM quiz_attempts WHERE quizId = :quizId ORDER BY completedAt DESC")
    fun observeAttemptsForQuiz(quizId: Long): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    fun observeAllAttempts(): Flow<List<QuizAttemptEntity>>

    @Insert
    suspend fun insertQuizAttempt(attempt: QuizAttemptEntity): Long

    @Query("SELECT * FROM flashcards ORDER BY updatedAt DESC")
    fun observeAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun observeFlashcardsForSubject(subjectId: Long): Flow<List<FlashcardEntity>>

    @Insert
    suspend fun insertFlashcard(card: FlashcardEntity): Long

    @Insert
    suspend fun insertFlashcards(cards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(card: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(card: FlashcardEntity)
}

@Dao
interface ProductivityDao {
    @Query("SELECT * FROM tasks ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, taskDateMillis ASC, priority DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId")
    suspend fun getReminderForTask(taskId: Long): TaskReminderEntity?

    @Query("SELECT * FROM task_reminders WHERE isScheduled = 1 AND triggerAtMillis > :now")
    suspend fun getPendingTaskReminders(now: Long): List<TaskReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTaskReminder(reminder: TaskReminderEntity): Long

    @Query("DELETE FROM task_reminders WHERE taskId = :taskId")
    suspend fun deleteRemindersForTask(taskId: Long)

    @Query("SELECT * FROM bills ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, dueDateMillis ASC")
    fun observeAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBill(id: Long): BillEntity?

    @Insert
    suspend fun insertBill(bill: BillEntity): Long

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Delete
    suspend fun deleteBill(bill: BillEntity)

    @Query("SELECT * FROM bill_reminders WHERE billId = :billId")
    suspend fun getReminderForBill(billId: Long): BillReminderEntity?

    @Query("SELECT * FROM bill_reminders WHERE isScheduled = 1 AND triggerAtMillis > :now")
    suspend fun getPendingBillReminders(now: Long): List<BillReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBillReminder(reminder: BillReminderEntity): Long

    @Query("DELETE FROM bill_reminders WHERE billId = :billId")
    suspend fun deleteRemindersForBill(billId: Long)

    @Query("SELECT * FROM study_plans ORDER BY updatedAt DESC")
    fun observeStudyPlans(): Flow<List<StudyPlanEntity>>

    @Insert
    suspend fun insertStudyPlan(plan: StudyPlanEntity): Long

    @Update
    suspend fun updateStudyPlan(plan: StudyPlanEntity)

    @Delete
    suspend fun deleteStudyPlan(plan: StudyPlanEntity)

    @Query("SELECT * FROM study_sessions WHERE planId = :planId ORDER BY orderIndex ASC")
    fun observeSessionsForPlan(planId: Long): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY orderIndex ASC")
    fun observeAllStudySessions(): Flow<List<StudySessionEntity>>

    @Insert
    suspend fun insertStudySession(session: StudySessionEntity): Long

    @Insert
    suspend fun insertStudySessions(sessions: List<StudySessionEntity>)

    @Update
    suspend fun updateStudySession(session: StudySessionEntity)

    @Delete
    suspend fun deleteStudySession(session: StudySessionEntity)
}

@Dao
interface LifestyleDao {
    @Query("SELECT * FROM wallet_transactions ORDER BY transactionDateMillis DESC, id DESC")
    fun observeWalletTransactions(): Flow<List<WalletTransactionEntity>>

    @Insert
    suspend fun insertTransaction(tx: WalletTransactionEntity): Long

    @Update
    suspend fun updateTransaction(tx: WalletTransactionEntity)

    @Delete
    suspend fun deleteTransaction(tx: WalletTransactionEntity)

    @Query("SELECT * FROM class_routines ORDER BY updatedAt DESC")
    fun observeClassRoutines(): Flow<List<ClassRoutineEntity>>

    @Insert
    suspend fun insertClassRoutine(routine: ClassRoutineEntity): Long

    @Update
    suspend fun updateClassRoutine(routine: ClassRoutineEntity)

    @Delete
    suspend fun deleteClassRoutine(routine: ClassRoutineEntity)

    @Query("SELECT * FROM timetable_entries ORDER BY startTime ASC")
    fun observeTimetableEntries(): Flow<List<TimetableEntryEntity>>

    @Insert
    suspend fun insertTimetableEntry(entry: TimetableEntryEntity): Long

    @Update
    suspend fun updateTimetableEntry(entry: TimetableEntryEntity)

    @Delete
    suspend fun deleteTimetableEntry(entry: TimetableEntryEntity)

    @Query("SELECT * FROM bus_schedules ORDER BY updatedAt DESC")
    fun observeBusSchedules(): Flow<List<BusScheduleEntity>>

    @Insert
    suspend fun insertBusSchedule(schedule: BusScheduleEntity): Long

    @Update
    suspend fun updateBusSchedule(schedule: BusScheduleEntity)

    @Delete
    suspend fun deleteBusSchedule(schedule: BusScheduleEntity)

    @Query("SELECT * FROM food_charts ORDER BY updatedAt DESC")
    fun observeFoodCharts(): Flow<List<FoodChartEntity>>

    @Insert
    suspend fun insertFoodChart(chart: FoodChartEntity): Long

    @Update
    suspend fun updateFoodChart(chart: FoodChartEntity)

    @Delete
    suspend fun deleteFoodChart(chart: FoodChartEntity)

    @Query("SELECT * FROM nutrition_suggestions ORDER BY createdAt DESC")
    fun observeNutritionSuggestions(): Flow<List<NutritionSuggestionEntity>>

    @Insert
    suspend fun insertNutritionSuggestion(suggestion: NutritionSuggestionEntity): Long

    @Delete
    suspend fun deleteNutritionSuggestion(suggestion: NutritionSuggestionEntity)

    @Query("SELECT * FROM emergency_contacts ORDER BY isFavorite DESC, name ASC")
    fun observeEmergencyContacts(): Flow<List<EmergencyContactEntity>>

    @Insert
    suspend fun insertEmergencyContact(contact: EmergencyContactEntity): Long

    @Update
    suspend fun updateEmergencyContact(contact: EmergencyContactEntity)

    @Delete
    suspend fun deleteEmergencyContact(contact: EmergencyContactEntity)

    @Query("SELECT * FROM portal_entries ORDER BY isFavorite DESC, displayOrder ASC, createdAt DESC")
    fun observePortalEntries(): Flow<List<PortalEntryEntity>>

    @Insert
    suspend fun insertPortalEntry(entry: PortalEntryEntity): Long

    @Update
    suspend fun updatePortalEntry(entry: PortalEntryEntity)

    @Update
    suspend fun updatePortalEntries(entries: List<PortalEntryEntity>)

    @Delete
    suspend fun deletePortalEntry(entry: PortalEntryEntity)
}

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_conversations WHERE id = :id")
    suspend fun getConversation(id: Long): AiConversationEntity?

    @Insert
    suspend fun insertConversation(conv: AiConversationEntity): Long

    @Update
    suspend fun updateConversation(conv: AiConversationEntity)

    @Delete
    suspend fun deleteConversation(conv: AiConversationEntity)

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesSync(conversationId: Long): List<AiMessageEntity>

    @Insert
    suspend fun insertMessage(msg: AiMessageEntity): Long

    @Delete
    suspend fun deleteMessage(msg: AiMessageEntity)

    @Query("SELECT * FROM pending_ai_requests ORDER BY createdAt DESC")
    fun observePendingRequests(): Flow<List<PendingAiRequestEntity>>

    @Insert
    suspend fun insertPendingRequest(req: PendingAiRequestEntity): Long

    @Update
    suspend fun updatePendingRequest(req: PendingAiRequestEntity)

    @Delete
    suspend fun deletePendingRequest(req: PendingAiRequestEntity)
}
