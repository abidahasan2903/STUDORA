package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.db.StudoraDatabase

data class ScheduleResult(
    val scheduled: Boolean,
    val exactDeliveryUsed: Boolean,
    val message: String
)

class ReminderScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_TASKS = "studora_tasks_channel"
        const val CHANNEL_BILLS = "studora_bills_channel"

        const val EXTRA_TYPE = "reminder_type"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_BODY = "reminder_body"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        const val TYPE_TASK = "TASK"
        const val TYPE_BILL = "BILL"
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for today, tomorrow, and upcoming student tasks"
                enableVibration(true)
            }
            val billChannel = NotificationChannel(
                CHANNEL_BILLS,
                "Bill Payment Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upcoming tuition, rent, and bill payment reminders"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(taskChannel)
            notificationManager.createNotificationChannel(billChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun areNotificationsEnabledInSystem(): Boolean {
        return notificationManager.areNotificationsEnabled() && hasNotificationPermission()
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleTaskReminder(
        taskId: Long,
        notificationId: Int,
        title: String,
        description: String,
        triggerAtMillis: Long
    ): ScheduleResult {
        ensureNotificationChannels()
        if (!areNotificationsEnabledInSystem()) {
            return ScheduleResult(
                scheduled = false,
                exactDeliveryUsed = false,
                message = "Notification permission is disabled. Task saved without alarm notification."
            )
        }
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return ScheduleResult(
                scheduled = false,
                exactDeliveryUsed = false,
                message = "Selected reminder time is already in the past."
            )
        }

        val pendingIntent = buildReminderPendingIntent(
            type = TYPE_TASK,
            recordId = taskId,
            notificationId = notificationId,
            title = "Task Reminder: $title",
            body = description.ifBlank { "Scheduled in STUDORA Tasks" }
        )

        return scheduleAlarmInternal(triggerAtMillis, pendingIntent)
    }

    fun scheduleBillReminder(
        billId: Long,
        notificationId: Int,
        title: String,
        amountFormatted: String,
        triggerAtMillis: Long
    ): ScheduleResult {
        ensureNotificationChannels()
        if (!areNotificationsEnabledInSystem()) {
            return ScheduleResult(
                scheduled = false,
                exactDeliveryUsed = false,
                message = "Notification permission is disabled. Bill saved without alarm notification."
            )
        }
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return ScheduleResult(
                scheduled = false,
                exactDeliveryUsed = false,
                message = "Bill reminder time is already in the past."
            )
        }

        val pendingIntent = buildReminderPendingIntent(
            type = TYPE_BILL,
            recordId = billId,
            notificationId = notificationId,
            title = "Bill Due Alert: $title",
            body = "Amount: $amountFormatted • Tap to review in STUDORA"
        )

        return scheduleAlarmInternal(triggerAtMillis, pendingIntent)
    }

    private fun scheduleAlarmInternal(triggerAtMillis: Long, pendingIntent: PendingIntent): ScheduleResult {
        return try {
            alarmManager.cancel(pendingIntent)
            val useExact = canScheduleExactAlarms()
            if (useExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                ScheduleResult(
                    scheduled = true,
                    exactDeliveryUsed = true,
                    message = "Reminder scheduled."
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                ScheduleResult(
                    scheduled = true,
                    exactDeliveryUsed = false,
                    message = "Reminder scheduled (Android battery optimization may adjust exact delivery window)."
                )
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                ScheduleResult(
                    scheduled = true,
                    exactDeliveryUsed = false,
                    message = "Reminder scheduled with standard Doze-aware delivery."
                )
            } catch (inner: Exception) {
                ScheduleResult(false, false, "Unable to schedule notification: ${inner.localizedMessage}")
            }
        } catch (e: Exception) {
            ScheduleResult(false, false, "Unable to schedule notification: ${e.localizedMessage}")
        }
    }

    fun cancelReminder(type: String, recordId: Long, notificationId: Int) {
        try {
            val pendingIntent = buildReminderPendingIntent(
                type = type,
                recordId = recordId,
                notificationId = notificationId,
                title = "",
                body = ""
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            notificationManager.cancel(notificationId)
        } catch (_: Exception) {
        }
    }

    suspend fun restoreAllFutureReminders(db: StudoraDatabase) {
        ensureNotificationChannels()
        if (!areNotificationsEnabledInSystem()) return
        val now = System.currentTimeMillis()
        val prodDao = db.productivityDao()
        prodDao.getPendingTaskReminders(now).forEach { rem ->
            val task = prodDao.getTask(rem.taskId)
            if (task != null && task.status == "PENDING" && task.isReminderEnabled) {
                scheduleTaskReminder(
                    taskId = task.id,
                    notificationId = rem.notificationId,
                    title = task.title,
                    description = task.description,
                    triggerAtMillis = rem.triggerAtMillis
                )
            }
        }
        prodDao.getPendingBillReminders(now).forEach { rem ->
            val bill = prodDao.getBill(rem.billId)
            if (bill != null && bill.status == "PENDING") {
                scheduleBillReminder(
                    billId = bill.id,
                    notificationId = rem.notificationId,
                    title = bill.title,
                    amountFormatted = String.format("%.2f", bill.amount),
                    triggerAtMillis = rem.triggerAtMillis
                )
            }
        }
    }

    private fun buildReminderPendingIntent(
        type: String,
        recordId: Long,
        notificationId: Int,
        title: String,
        body: String
    ): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "com.example.studora.ACTION_REMINDER_${type}_$recordId"
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_RECORD_ID, recordId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
