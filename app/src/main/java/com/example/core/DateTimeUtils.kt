package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val dayHeaderFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())

    fun startOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun todayMidnight(): Long = startOfDayMillis(System.currentTimeMillis())

    fun tomorrowMidnight(): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMidnight()
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun addDays(midnightMillis: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = midnightMillis
            add(Calendar.DAY_OF_YEAR, days)
        }
        return cal.timeInMillis
    }

    fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
    fun formatShortDate(millis: Long): String = shortDateFormat.format(Date(millis))
    fun formatDayHeader(millis: Long): String = dayHeaderFormat.format(Date(millis))
    fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

    fun formatMinutesOfDay(minutes: Int?): String {
        if (minutes == null) return "No time set"
        val h = (minutes / 60).coerceIn(0, 23)
        val m = (minutes % 60).coerceIn(0, 59)
        return formatHourMinute(h, m)
    }

    fun formatHourMinute(hour24: Int, minute: Int): String {
        val amPm = if (hour24 >= 12) "PM" else "AM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", h12, minute, amPm)
    }

    fun formatDurationMs(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    fun formatCurrency(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }
}
