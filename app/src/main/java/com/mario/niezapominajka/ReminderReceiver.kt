package com.mario.niezapominajka

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "Niezapominajka"

private fun getNextReminderDate(
    date: Long,
    hour: Int,
    minute: Int,
    repeatType: String,
    monthlyDay: Int
): Long? {
    if (repeatType == "NONE") return null

    if (date <= 0L || hour !in 0..23 || minute !in 0..59) {
        Log.e(TAG, "Nieprawidłowe dane terminu: date=$date, hour=$hour, minute=$minute")
        return null
    }

    if (repeatType !in setOf("DAILY", "WEEKLY", "MONTHLY")) {
        Log.e(TAG, "Nieznany typ powtarzania: $repeatType")
        return null
    }

    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = date
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    val targetDay = if (monthlyDay in 1..31) {
        monthlyDay
    } else {
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun advance() {
        when (repeatType) {
            "DAILY" -> calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)

            "WEEKLY" -> calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)

            "MONTHLY" -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.add(java.util.Calendar.MONTH, 1)

                val maxDay = calendar.getActualMaximum(
                    java.util.Calendar.DAY_OF_MONTH
                )

                calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    minOf(targetDay, maxDay)
                )
            }
        }
    }

    // Wyznacz pierwszy termin po poprzednim alarmie.
    advance()

    // Jeżeli alarm został obsłużony z opóźnieniem, pomiń terminy,
    // które już minęły.
    val now = System.currentTimeMillis()
    var attempts = 0

    while (calendar.timeInMillis <= now && attempts < 500) {
        advance()
        attempts++
    }

    val result = calendar.timeInMillis

    return if (result > now) {
        result
    } else {
        Log.e(TAG, "Nie udało się wyznaczyć przyszłego terminu.")
        null
    }
}

private suspend fun updateReminderDate(
    context: Context,
    reminderId: Long,
    nextDate: Long
): Boolean {
    if (reminderId == -1L) {
        Log.e(TAG, "Nie można zapisać daty: nieprawidłowy identyfikator.")
        return false
    }

    val reminders = loadReminders(context)

    var found = false

    val updatedReminders = reminders.map { reminder ->
        if (reminder.id == reminderId) {
            found = true
            reminder.copy(date = nextDate)
        } else {
            reminder
        }
    }

    if (!found) {
        Log.e(TAG, "Nie znaleziono przypomnienia o ID=$reminderId.")
        return false
    }

    saveReminders(context, updatedReminders)

    Log.d(
        TAG,
        "Zapisano nową datę: ID=$reminderId, nextDate=$nextDate"
    )

    return true
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderText =
            intent.getStringExtra("reminder_text") ?: "Masz przypomnienie"

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

        val monthlyDay =
            intent.getIntExtra("monthly_day", 0)

        val isSnooze =
            intent.getBooleanExtra("is_snooze", false)

        val nextDate = if (isSnooze) {
            null
        } else {
            getNextReminderDate(
                reminderDate,
                reminderHour,
                reminderMinute,
                repeatType,
                monthlyDay
            )
        }

        Log.d(
            TAG,
            "Uruchomiono alarm: ID=$reminderId, " +
                    "repeat=$repeatType, date=$reminderDate, " +
                    "nextDate=$nextDate, snooze=$isSnooze"
        )

        // Zachowujemy czas na zakończenie zapisu przed zakończeniem
        // obsługi odbiornika. Dzięki temu ekran aplikacji ma większą
        // szansę odczytać już zaktualizowaną datę.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                
if (nextDate != null) {
    try {
        scheduleNextReminder(
            context = context,
            reminderText = reminderText,
            soundEnabled = soundEnabled,
            speakText = speakText,
            voiceFilePath = voiceFilePath,
            audioUri = audioUri,
            reminderId = reminderId,
            repeatType = repeatType,
            nextDate = nextDate,
            hour = reminderHour,
            minute = reminderMinute,
            monthlyDay = monthlyDay
        )

        updateReminderDate(
            context,
            reminderId,
            nextDate
        )
    } catch (e: SecurityException) {
        Log.e(
            TAG,
            "Brak uprawnienia do zaplanowania dokładnego alarmu. " +
                "Data przypomnienia nie została zaktualizowana.",
            e
        )
    } catch (e: Exception) {
        Log.e(
            TAG,
            "Nie udało się zaplanować kolejnego alarmu. " +
                "Data przypomnienia nie została zaktualizowana.",
            e
        )
    }
}


                showAlarm(
                    context = context,
                    reminderText = reminderText,
                    soundEnabled = soundEnabled,
                    speakText = speakText,
                    voiceFilePath = voiceFilePath,
                    audioUri = audioUri,
                    reminderId = reminderId,
                    repeatType = repeatType
                )
            } catch (e: Exception) {
                Log.e(TAG, "Błąd obsługi przypomnienia.", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    
private fun scheduleNextReminder(
    context: Context,
    reminderText: String,
    soundEnabled: Boolean,
    speakText: Boolean,
    voiceFilePath: String?,
    audioUri: String?,
    reminderId: Long,
    repeatType: String,
    nextDate: Long,
    hour: Int,
    minute: Int,
    monthlyDay: Int
): Boolean {
    if (reminderId == -1L) {
        Log.e(TAG, "Nie można zaplanować alarmu: nieprawidłowe ID.")
        return false
    }

    val alarmManager = context.getSystemService(
        Context.ALARM_SERVICE
    ) as AlarmManager

    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !alarmManager.canScheduleExactAlarms()
    ) {
        Log.e(
            TAG,
            "Brak uprawnienia do alarmów dokładnych. ID=$reminderId"
        )
        return false
    }

    return try {
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
            putExtra("reminder_hour", hour)
            putExtra("reminder_minute", minute)
            putExtra("monthly_day", monthlyDay)
        }

        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextDate,
            nextPendingIntent
        )

        Log.d(
            TAG,
            "Zaplanowano dokładny kolejny alarm: " +
                "ID=$reminderId, date=$nextDate"
        )

        true
    } catch (e: Exception) {
        Log.e(
            TAG,
            "Nie udało się zaplanować kolejnego alarmu. ID=$reminderId",
            e
        )
        false
    }
}


    private fun showAlarm(
        context: Context,
        reminderText: String,
        soundEnabled: Boolean,
        speakText: Boolean,
        voiceFilePath: String?,
        audioUri: String?,
        reminderId: Long,
        repeatType: String
    ) {
        if (soundEnabled && !speakText) {
            val serviceIntent = Intent(
                context,
                AlarmService::class.java
            ).apply {
                putExtra("reminder_text", reminderText)
                putExtra("sound_enabled", soundEnabled)
                putExtra("speak_text", speakText)
                putExtra("voice_file_path", voiceFilePath)
                putExtra("audio_uri", audioUri)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się uruchomić usługi alarmu.", e)
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
            .setFullScreenIntent(alarmPendingIntent, true)
            .build()

        if (
            Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                reminderId.hashCode(),
                notification
            )
        }
    }
}
