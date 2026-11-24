package com.example

import android.app.Application
import com.example.core.AiService
import com.example.core.AudioService
import com.example.core.BackupManager
import com.example.core.ConnectivityObserver
import com.example.core.LocalStorageManager
import com.example.data.db.StudoraDatabase
import com.example.data.repo.StudoraRepository
import com.example.notifications.ReminderScheduler

class StudoraApplication : Application() {
    val database: StudoraDatabase by lazy { StudoraDatabase.getInstance(this) }
    val storageManager: LocalStorageManager by lazy { LocalStorageManager(this) }
    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(this) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }
    val audioService: AudioService by lazy { AudioService(this, storageManager) }
    val aiService: AiService by lazy { AiService() }
    val backupManager: BackupManager by lazy { BackupManager(this, database, storageManager) }

    val repository: StudoraRepository by lazy {
        StudoraRepository(
            context = this,
            db = database,
            storage = storageManager,
            reminderScheduler = reminderScheduler,
            aiService = aiService,
            audioService = audioService,
            backupManager = backupManager,
            connectivityObserver = connectivityObserver
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Note: App Check is strictly initialized in MainActivity.onCreate() with the launch intent.
        reminderScheduler.ensureNotificationChannels()
    }
}
