package com.example.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: ReminderScheduler.TYPE_TASK
        val recordId = intent.getLongExtra(ReminderScheduler.EXTRA_RECORD_ID, 0L)
        val notificationId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, recordId.toInt())
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "STUDORA Reminder"
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: "Tap to open STUDORA"

        val scheduler = ReminderScheduler(context)
        scheduler.ensureNotificationChannels()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_module", if (type == ReminderScheduler.TYPE_BILL) "bills" else "tasks")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 10_000,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (type == ReminderScheduler.TYPE_BILL) {
            ReminderScheduler.CHANNEL_BILLS
        } else {
            ReminderScheduler.CHANNEL_TASKS
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.studora_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }
}
