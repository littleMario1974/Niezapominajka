
package com.mario.niezapominajka

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
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
private const val REMINDERS_CHANNEL_ID = "reminders_alarm_v3"


class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderText =
            intent.getStringExtra("reminder_text") ?: "Masz przypomnienie"

        val soundEnabled =
            intent.getBooleanExtra("sound_enabled", true)

        val speakText =
            intent.getBooleanExtra("speak_text", false)

        val vibrationEnabled = intent.getBooleanExtra(
            "vibration_enabled",
            !speakText && intent.getStringExtra("audio_uri").isNullOrBlank() &&
                intent.getStringExtra("voice_file_path").isNullOrBlank()
        )

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

        // DataStore może mieć starszą datę bazową, gdy alarm zaplanowano
        // po nadrobieniu zaległości; sprawdzamy wersję faktycznie zapisaną.
        val reminderStoredDate =
            intent.getLongExtra("reminder_stored_date", reminderDate)

        val reminderHour =
            intent.getIntExtra("reminder_hour", 0)

        val reminderMinute =
            intent.getIntExtra("reminder_minute", 0)

        val monthlyDay =
            intent.getIntExtra("monthly_day", 0)

        val isSnooze =
            intent.getBooleanExtra("is_snooze", false)

        val nextDate = if (isSnooze || repeatType == "NONE") {
            null
        } else {
            nextFutureOccurrence(
                date = reminderDate,
                hour = reminderHour,
                minute = reminderMinute,
                repeatType = repeatType,
                monthlyDay = monthlyDay,
                nowMillis = System.currentTimeMillis()
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
                // Drzemka jest niezależnym, pięciominutowym alarmem i nie
                // posiada własnej wersji daty w DataStore.
                val reminderStillCurrent = if (isSnooze) {
                    true
                } else {
                    try {
                        updateReminderDateIfUnchanged(
                            context = context,
                            reminderId = reminderId,
                            expectedDate = reminderStoredDate,
                            expectedText = reminderText,
                            expectedHour = reminderHour,
                            expectedMinute = reminderMinute,
                            expectedSoundEnabled = soundEnabled,
                            expectedVibrationEnabled = vibrationEnabled,
                            expectedSpeakText = speakText,
                            expectedVoiceFilePath = voiceFilePath,
                            expectedAudioUri = audioUri,
                            expectedRepeatType = repeatType,
                            expectedMonthlyDay = monthlyDay,
                            nextDate = nextDate
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Nie udało się zweryfikować aktualności alarmu.", e)
                        false
                    }
                }

                if (reminderStillCurrent) {
                    if (nextDate != null) {
                        try {
                            scheduleNextReminder(
                                context = context,
                                reminderText = reminderText,
                                soundEnabled = soundEnabled,
                                vibrationEnabled = vibrationEnabled,
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
                        } catch (e: Exception) {
                            Log.e(TAG, "Błąd planowania kolejnego alarmu.", e)
                        }
                    }

                    showAlarm(
                        context = context,
                        reminderText = reminderText,
                        soundEnabled = soundEnabled,
                        vibrationEnabled = vibrationEnabled,
                        speakText = speakText,
                        voiceFilePath = voiceFilePath,
                        audioUri = audioUri,
                        reminderId = reminderId,
                        repeatType = repeatType
                    )
                } else {
                    Log.i(
                        TAG,
                        "Zignorowano nieaktualny alarm ID=$reminderId; " +
                            "przypomnienie zmieniono lub usunięto."
                    )
                }
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
        vibrationEnabled: Boolean,
        speakText: Boolean,
        voiceFilePath: String?,
        audioUri: String?,
        reminderId: Long,
        repeatType: String,
        nextDate: Long,
        hour: Int,
        minute: Int,
        monthlyDay: Int
    ) {
        if (reminderId == -1L) {
            Log.e(TAG, "Nie można zaplanować alarmu: nieprawidłowe ID.")
            return
        }

        val nextIntent = Intent(
            context,
            ReminderReceiver::class.java
        ).apply {
            putExtra("reminder_text", reminderText)
            putExtra("sound_enabled", soundEnabled)
            putExtra("speak_text", speakText)
            putExtra("vibration_enabled", vibrationEnabled)
            putExtra("voice_file_path", voiceFilePath)
            putExtra("audio_uri", audioUri)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_type", repeatType)
            putExtra("reminder_date", nextDate)
            putExtra("reminder_stored_date", nextDate)
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

        val alarmManager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextDate,
                nextPendingIntent
            )
        } else {
            // Rezerwowe planowanie, gdy nie ma uprawnienia
            // do alarmów dokładnych.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextDate,
                nextPendingIntent
            )

            Log.w(
                TAG,
                "Brak uprawnienia do alarmów dokładnych. " +
                        "Ustawiono alarm rezerwowy."
            )
        }

        Log.d(
            TAG,
            "Zaplanowano kolejny alarm: ID=$reminderId, date=$nextDate"
        )
    }

    private fun showAlarm(
        context: Context,
        reminderText: String,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        speakText: Boolean,
        voiceFilePath: String?,
        audioUri: String?,
        reminderId: Long,
        repeatType: String
    ) {
        val keyguardManager = context.getSystemService(
            Context.KEYGUARD_SERVICE
        ) as KeyguardManager
        val isDeviceLocked = keyguardManager.isKeyguardLocked
        val shouldVibrate = isDeviceLocked && vibrationEnabled


        // Dźwięk i TTS uruchamiamy wyłącznie przy zablokowanym telefonie.
        if (isDeviceLocked && (soundEnabled || speakText)) {
            val serviceIntent = Intent(
                context,
                AlarmService::class.java
            ).apply {
                putExtra("reminder_text", reminderText)
                putExtra("reminder_id", reminderId)
                putExtra("sound_enabled", soundEnabled)
                putExtra("speak_text", speakText)
                putExtra("vibration_enabled", vibrationEnabled)
                putExtra("voice_file_path", voiceFilePath)
                putExtra("audio_uri", audioUri)
            }

            try {
                context.startForegroundService(serviceIntent)
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
            putExtra("should_vibrate", shouldVibrate)
            putExtra("vibration_enabled", vibrationEnabled)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_type", repeatType)
        }

        val alarmPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(REMINDERS_CHANNEL_ID) == null
        ) {
            val silentChannel = NotificationChannel(
                REMINDERS_CHANNEL_ID,
                "Przypomnienia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ciche powiadomienia z aplikacji Niezapominajka"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(silentChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(
            context,
            REMINDERS_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Niezapominajka")
            .setContentText(reminderText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(false)

        // Powiadomienie pełnoekranowe jest używane także przy odblokowanym
        // ekranie. Android 13+ pokazuje wtedy trwały baner heads-up,
        // który użytkownik może odrzucić lub uśpić. Przy zablokowanym
        // ekranie nadal uruchamia się AlarmActivity.
        notificationBuilder.setFullScreenIntent(alarmPendingIntent, true)

        val notification = notificationBuilder.build()

        // Powiadomienie pozostaje w centrum powiadomień do ręcznego
        // odrzucenia. Android sam ukrywa krótki baner heads-up po chwili.
        val notificationsAllowed =
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (notificationsAllowed) {
            NotificationManagerCompat.from(context).notify(
                reminderId.hashCode(),
                notification
            )
        }
    }
}
