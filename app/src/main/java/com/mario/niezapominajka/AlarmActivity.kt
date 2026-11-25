package com.mario.niezapominajka

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.NotificationManagerCompat
import android.media.AudioManager

class AlarmActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val reminderText =
            intent.getStringExtra("reminder_text") ?: "Przypomnienie"

        val reminderId = intent.getLongExtra("reminder_id", -1L)
        val soundEnabled =
            intent.getBooleanExtra("sound_enabled", true)

        val speakText =
            intent.getBooleanExtra("speak_text", false)
        val repeatType = intent.getStringExtra("repeat_type") ?: "NONE"
        val shouldVibrate = intent.getBooleanExtra("should_vibrate", false)

        val voiceFilePath =
            intent.getStringExtra("voice_file_path")

        val audioUri =
            intent.getStringExtra("audio_uri")

        startAlarm(shouldVibrate)
        setContent {
            var dismissed by remember { mutableStateOf(false) }

            val infiniteTransition = rememberInfiniteTransition(
                label = "alarmColor"
            )

            val backgroundColor by infiniteTransition.animateColor(
                initialValue = Color.Red,
                targetValue = Color.Blue,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "backgroundColor"
            )

            if (!dismissed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⏰ PRZYPOMNIENIE",
                        color = Color.White,
                        fontSize = 24.sp
                    )

                    Text(
                        text = reminderText,
                        color = Color.White,
                        fontSize = 40.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            dismissed = true
                            stopAlarm()

                            stopSharedAlarmService(reminderId)

                            if (reminderId != -1L) {
                                NotificationManagerCompat.from(this@AlarmActivity)
                                    .cancel(reminderId.hashCode())
                            }

                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                    ) {
                        Text("Wyłącz alarm")
                    }

                    Button(
                        onClick = {
                            val snoozeIntent = Intent(
                                this@AlarmActivity,
                                ReminderReceiver::class.java
                            ).apply {
                                putExtra("reminder_text", reminderText)
                                putExtra("sound_enabled", soundEnabled)
                                putExtra("speak_text", speakText)
                                putExtra("voice_file_path", voiceFilePath)
                                putExtra("audio_uri", audioUri)
                                putExtra("reminder_id", reminderId)
                                putExtra("repeat_type", repeatType)
                                putExtra("is_snooze", true)
                            }

                            val snoozePendingIntent = PendingIntent.getBroadcast(
                                this@AlarmActivity,
                                reminderId.hashCode() xor 0x5A0E,
                                snoozeIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )

                            val alarmManager =
                                getSystemService(ALARM_SERVICE) as android.app.AlarmManager

                            val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000L

                            if (android.os.Build.VERSION.SDK_INT < 31 ||
                                alarmManager.canScheduleExactAlarms()
                            ) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    android.app.AlarmManager.RTC_WAKEUP,
                                    snoozeTime,
                                    snoozePendingIntent
                                )
                            } else {
                                alarmManager.setAndAllowWhileIdle(
                                    android.app.AlarmManager.RTC_WAKEUP,
                                    snoozeTime,
                                    snoozePendingIntent
                                )
                            }

                            dismissed = true
                            stopAlarm()

                            stopSharedAlarmService(reminderId)
                            if (reminderId != -1L) {
                                NotificationManagerCompat.from(this@AlarmActivity)
                                    .cancel(reminderId.hashCode())
                            }
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                    ) {
                        Text("Drzemka 5 min")
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            audioManager.adjustStreamVolume(
                AudioManager.STREAM_ALARM,
                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    AudioManager.ADJUST_RAISE
                } else {
                    AudioManager.ADJUST_LOWER
                },
                AudioManager.FLAG_SHOW_UI
            )

            return true
        }

        return super.onKeyDown(keyCode, event)
    }
    private fun stopSharedAlarmService(reminderId: Long) {
        if (reminderId == -1L) return
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
            putExtra(AlarmService.EXTRA_REMINDER_ID, reminderId)
        }
        startService(stopIntent)
    }

    private fun startAlarm(shouldVibrate: Boolean) {
        if (!shouldVibrate) return

        vibrator =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

        val vibrationPattern = longArrayOf(0, 500, 300, 500, 300)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(vibrationPattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(vibrationPattern, 0)
        }
    }



    private fun stopAlarm() {
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopAlarm()

        super.onDestroy()
    }
}

