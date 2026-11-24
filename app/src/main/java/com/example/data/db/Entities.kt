package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 1. app_settings
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val languagePreference: String = "English",
    val notificationsEnabled: Boolean = true,
    val defaultReminderHour: Int = 9,
    val defaultReminderMinute: Int = 0,
    val ttsSpeechRate: Float = 1.0f,
    val ttsLanguage: String = "en-US",
    val updatedAt: Long = System.currentTimeMillis()
)

// 2. local_profile
@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    val studentType: String = "University Student",
    val academicLevel: String = "",
    val preferredLanguage: String = "English",
    val studyGoals: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

// 3. subjects
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String = "",
    val teacherName: String = "",
    val academicLevel: String = "",
    val description: String = "",
    val iconName: String = "Book",
    val colorHex: String = "#0F2547",
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 4. topics
@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val description: String = "",
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 5. documents
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val topicId: Long? = null,
    val title: String,
    val filePath: String,
    val fileType: String = "PDF", // PDF, IMAGE, OTHER
    val mimeType: String = "application/pdf",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val sourceType: String = "IMPORT", // IMPORT, SCAN, CAMERA
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 6. written_notes
@Entity(
    tableName = "written_notes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId"), Index("documentId")]
)
data class WrittenNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val topicId: Long? = null,
    val documentId: Long? = null,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 7. ai_notes
@Entity(
    tableName = "ai_notes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId"), Index("documentId")]
)
data class AiNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val topicId: Long? = null,
    val documentId: Long? = null,
    val title: String,
    val prompt: String,
    val content: String,
    val modelName: String = "gemini-3.8-flash",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 8. audio_recordings
@Entity(
    tableName = "audio_recordings",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId"), Index("documentId")]
)
data class AudioRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val topicId: Long? = null,
    val documentId: Long? = null,
    val title: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 9. ai_audio
@Entity(
    tableName = "ai_audio",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId")]
)
data class AiAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val topicId: Long? = null,
    val sourceType: String, // WRITTEN_NOTE, AI_NOTE, DOCUMENT, TOPIC
    val sourceId: Long? = null,
    val title: String,
    val sourceTextPreview: String = "",
    val filePath: String,
    val languageCode: String = "en-US",
    val voiceName: String = "Default",
    val speechRate: Float = 1.0f,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 10. quizzes
@Entity(
    tableName = "quizzes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId"), Index("documentId")]
)
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val topicId: Long? = null,
    val documentId: Long? = null,
    val title: String,
    val questionType: String = "MCQ", // MCQ, TRUE_FALSE, SHORT_ANSWER, VIVA
    val difficulty: String = "Medium",
    val questionCount: Int = 5,
    val createdAt: Long = System.currentTimeMillis()
)

// 11. quiz_questions
@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val questionText: String,
    val questionType: String,
    val optionsJson: String = "[]",
    val correctAnswer: String,
    val explanation: String = "",
    val orderIndex: Int = 0
)

// 12. quiz_attempts
@Entity(
    tableName = "quiz_attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val score: Int,
    val totalQuestions: Int,
    val answersJson: String = "{}",
    val completedAt: Long = System.currentTimeMillis()
)

// 13. flashcards
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("topicId"), Index("documentId")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val topicId: Long? = null,
    val documentId: Long? = null,
    val frontQuestion: String,
    val backAnswer: String,
    val masteryStatus: String = "NEW", // NEW, KNOWN, DIFFICULT
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 14. study_plans
@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectsSummary: String,
    val academicLevel: String = "",
    val examDateMillis: Long? = null,
    val dailyStudyMinutes: Int = 180,
    val preferredHours: String = "Evening",
    val studyGoals: String = "",
    val difficultSubjects: String = "",
    val breakPreferences: String = "10 min every 50 min",
    val isAiGenerated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 15. study_sessions
@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayOfWeek: String, // Monday..Sunday or Daily
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val subjectName: String,
    val sessionType: String = "STUDY", // STUDY, BREAK, REVISION
    val focusTopic: String = "",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val orderIndex: Int = 0
)

// 16. class_routines
@Entity(tableName = "class_routines")
data class ClassRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val fileType: String = "IMAGE", // IMAGE, PDF
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 17. timetable_entries
@Entity(tableName = "timetable_entries")
data class TimetableEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectName: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: String = "",
    val teacher: String = "",
    val colorHex: String = "#0F2547",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 18. bus_schedules
@Entity(tableName = "bus_schedules")
data class BusScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isDocumentRecord: Boolean = true,
    val title: String,
    val filePath: String = "",
    val fileType: String = "IMAGE",
    val pageCount: Int = 1,
    val routeName: String = "",
    val busName: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val startingPoint: String = "",
    val destination: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 19. tasks
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val taskDateMillis: Long,
    val reminderTimeMinutes: Int? = null, // minutes from midnight e.g. 540 = 09:00
    val isReminderEnabled: Boolean = false,
    val imagePath: String? = null,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val status: String = "PENDING", // PENDING, COMPLETED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 20. task_reminders
@Entity(
    tableName = "task_reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val notificationId: Int,
    val triggerAtMillis: Long,
    val isScheduled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

// 21. bills
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val dueDateMillis: Long,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val reminderIntervalDays: Int = 1, // 1, 2, 3, 7, or 0 for custom
    val customReminderMillis: Long? = null,
    val description: String = "",
    val status: String = "PENDING", // PENDING, PAID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 22. bill_reminders
@Entity(
    tableName = "bill_reminders",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId")]
)
data class BillReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val notificationId: Int,
    val triggerAtMillis: Long,
    val isScheduled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

// 23. wallet_transactions
@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME, EXPENSE
    val amount: Double,
    val category: String,
    val description: String = "",
    val transactionDateMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 24. food_charts
@Entity(tableName = "food_charts")
data class FoodChartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val fileType: String = "IMAGE",
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 25. nutrition_suggestions
@Entity(
    tableName = "nutrition_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = FoodChartEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodChartId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("foodChartId")]
)
data class NutritionSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodChartId: Long? = null,
    val title: String,
    val prompt: String,
    val extractedInfo: String,
    val aiSuggestions: String,
    val createdAt: Long = System.currentTimeMillis()
)

// 26. emergency_contacts
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String, // FAMILY, TRUSTED_FRIENDS, MEDICAL, POLICE, FIRE_SERVICE, CAMPUS_SECURITY, OTHER
    val phoneNumber: String,
    val description: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isVerifiedOfficial: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 27. portal_entries
@Entity(tableName = "portal_entries")
data class PortalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val websiteName: String,
    val websiteUrl: String,
    val description: String = "",
    val category: String = "Education",
    val customIconPath: String? = null,
    val isFavorite: Boolean = false,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null
)

// 28. ai_conversations
@Entity(tableName = "ai_conversations")
data class AiConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val contextType: String? = null, // SUBJECT, TOPIC, DOCUMENT, WRITTEN_NOTE, AI_NOTE
    val contextId: Long? = null,
    val contextLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 29. ai_messages
@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // USER, ASSISTANT, ERROR
    val content: String,
    val contextSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// 30. pending_ai_requests
@Entity(tableName = "pending_ai_requests")
data class PendingAiRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long? = null,
    val prompt: String,
    val contextSummary: String = "",
    val contextPayload: String = "",
    val status: String = "PENDING", // PENDING, FAILED
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
