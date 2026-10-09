package com.mario.niezapominajka

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch



private fun getNextReminderDate(
    date: Long,
    hour: Int,
    minute: Int,
    repeatType: String,
    monthlyDay: Int
): Long? {

    if (repeatType == "NONE") {
        return null
    }

    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = date

        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    when (repeatType) {

        "DAILY" -> {
            calendar.add(
                java.util.Calendar.DAY_OF_MONTH,
                1
            )
        }

        "WEEKLY" -> {
            calendar.add(
                java.util.Calendar.DAY_OF_MONTH,
                7
            )
        }

        "MONTHLY" -> {
            val targetDay = if (monthlyDay in 1..31) {
                monthlyDay
            } else {
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            }

            calendar.set(
                java.util.Calendar.DAY_OF_MONTH,
                1
            )

            calendar.add(
                java.util.Calendar.MONTH,
                1
            )

            val maxDay = calendar.getActualMaximum(
                java.util.Calendar.DAY_OF_MONTH
            )

            calendar.set(
                java.util.Calendar.DAY_OF_MONTH,
                minOf(targetDay, maxDay)
            )
        }
    }

    return calendar.timeInMillis
}

private suspend fun updateReminderDate(
    context: Context,
    reminderId: Long,
    nextDate: Long
) {

    val reminders = loadReminders(context)

    val updatedReminders = reminders.map { reminder ->

        if (reminder.id == reminderId) {

            reminder.copy(
                date = nextDate
            )

        } else {

            reminder
        }
    }

    saveReminders(
        context,
        updatedReminders
    )
}
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val reminderText =
            intent.getStringExtra("reminder_text")
                ?: "Masz przypomnienie"

        val soundEnabled =
            intent.getBooleanExtra("sound_enabled", true)
        val speakText =
            intent.getBooleanExtra("speak_text", false)

        val voiceFilePath =
            intent.getStringExtra("voice_file_path")
        val audioUri =
            intent.getStringExtra("audio_uri")
        val reminderId =
            intent.getLongExtra("reminder_id", -1L)
        val repeatType =
            intent.getStringExtra("repeat_type") ?: "NONE"

        val reminderDate =
            intent.getLongExtra("reminder_date", 0L)

        val reminderHour =
            intent.getIntExtra("reminder_hour", 0)

        val reminderMinute =
            intent.getIntExtra("reminder_minute", 0)

      
val monthlyDay = intent.getIntExtra(
    "monthly_day",
    0
)

val nextDate = getNextReminderDate(
    reminderDate,
    reminderHour,
    reminderMinute,
    repeatType,
    monthlyDay
)
        if (nextDate != null) {

            val pendingResult = goAsync()

            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.IO
            ).launch {

                try {

                    updateReminderDate(
                        context,
                        reminderId,
                        nextDate
                    )

                } finally {

                    pendingResult.finish()
                }
            }
        }
        if (nextDate != null) {

            val nextIntent = Intent(
                context,
                ReminderReceiver::class.java
            ).apply {
                putExtra("reminder_text", reminderText)
                putExtra("sound_enabled", soundEnabled)
                putExtra("speak_text", speakText)
                putExtra("voice_file_path", voiceFilePath)
                putExtra("audio_uri", audioUri)
                putExtra("reminder_id", reminderId)
                putExtra("repeat_type", repeatType)
                putExtra("reminder_date", nextDate)
                
putExtra("reminder_hour", reminderHour)
putExtra("reminder_minute", reminderMinute)
putExtra("monthly_day", monthlyDay)
            }

            val nextPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    reminderId.hashCode(),
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            val alarmManager =
                context.getSystemService(
                    Context.ALARM_SERVICE
                ) as android.app.AlarmManager

            if (
                android.os.Build.VERSION.SDK_INT < 31 ||
                alarmManager.canScheduleExactAlarms()
            ) {

                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextDate,
                    nextPendingIntent
                )
            }
        }

        val alarmIntent = Intent(
            context,
            AlarmActivity::class.java
        ).apply {
            putExtra("reminder_text", reminderText)
            putExtra("sound_enabled", soundEnabled)
            putExtra("speak_text", speakText)
            putExtra("voice_file_path", voiceFilePath)
            putExtra("audio_uri", audioUri)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_type", repeatType)
        }

        val alarmPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            "reminders"
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Niezapominajka")
            .setContentText(reminderText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(
                alarmPendingIntent,
                true
            )
            .build()

        if (
            android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(
                    System.currentTimeMillis().toInt(),
                    notification
                )
        }
    }
}
