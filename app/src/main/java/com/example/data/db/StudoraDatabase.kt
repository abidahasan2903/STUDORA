package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppSettingsEntity::class,
        LocalProfileEntity::class,
        SubjectEntity::class,
        TopicEntity::class,
        DocumentEntity::class,
        WrittenNoteEntity::class,
        AiNoteEntity::class,
        AudioRecordingEntity::class,
        AiAudioEntity::class,
        QuizEntity::class,
        QuizQuestionEntity::class,
        QuizAttemptEntity::class,
        FlashcardEntity::class,
        StudyPlanEntity::class,
        StudySessionEntity::class,
        ClassRoutineEntity::class,
        TimetableEntryEntity::class,
        BusScheduleEntity::class,
        TaskEntity::class,
        TaskReminderEntity::class,
        BillEntity::class,
        BillReminderEntity::class,
        WalletTransactionEntity::class,
        FoodChartEntity::class,
        NutritionSuggestionEntity::class,
        EmergencyContactEntity::class,
        PortalEntryEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        PendingAiRequestEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudoraDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun academicDao(): AcademicDao
    abstract fun learningDao(): LearningDao
    abstract fun productivityDao(): ProductivityDao
    abstract fun lifestyleDao(): LifestyleDao
    abstract fun aiDao(): AiDao

    fun checkpoint() {
        openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
            .close()
    }

    companion object {
        const val DB_NAME = "studora_local.db"

        @Volatile
        private var INSTANCE: StudoraDatabase? = null

        fun getInstance(context: Context): StudoraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): StudoraDatabase {
            return Room.databaseBuilder(
                appContext,
                StudoraDatabase::class.java,
                DB_NAME
            )
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .fallbackToDestructiveMigration(false)
                .build()
        }

        fun closeAndResetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
