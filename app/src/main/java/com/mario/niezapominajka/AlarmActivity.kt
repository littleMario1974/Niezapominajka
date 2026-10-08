package com.mario.niezapominajka

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
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

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val reminderText =
            intent.getStringExtra("reminder_text") ?: "Przypomnienie"

        val soundEnabled =
            intent.getBooleanExtra("sound_enabled", true)

        val voiceFilePath =
            intent.getStringExtra("voice_file_path")

        val audioUri =
            intent.getStringExtra("audio_uri")

        startAlarm(
            soundEnabled,
            voiceFilePath,
            audioUri
        )
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
                        modifier = Modifier.padding(top = 24.dp)
                    )

                    Button(
                        onClick = {
                            dismissed = true
                            stopAlarm()
                            finish()
                        },
                        modifier = Modifier.padding(top = 40.dp)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }

    private fun startAlarm(
        soundEnabled: Boolean,
        voiceFilePath: String?,
        audioUri: String?
    ) {

        if (soundEnabled) {

            if (!audioUri.isNullOrEmpty()) {

                mediaPlayer = MediaPlayer().apply {

                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_MUSIC
                            )
                            .build()
                    )

                    setDataSource(
                        this@AlarmActivity,
                        android.net.Uri.parse(audioUri)
                    )

                    isLooping = true

                    prepare()
                    start()
                }

            } else if (!voiceFilePath.isNullOrEmpty()) {

                mediaPlayer = MediaPlayer().apply {

                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_MUSIC
                            )
                            .build()
                    )

                    setDataSource(voiceFilePath)

                    isLooping = true

                    prepare()
                    start()
                }

            } else {

                val alarmUri =
                    RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM
                    )

                mediaPlayer = MediaPlayer().apply {

                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
                    )

                    setDataSource(
                        this@AlarmActivity,
                        alarmUri
                    )

                    isLooping = true

                    prepare()
                    start()
                }
            }
        }
        vibrator =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(VIBRATOR_MANAGER_SERVICE)
                            as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

        val vibrationPattern = longArrayOf(
            0,
            500,
            300,
            500,
            300
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    vibrationPattern,
                    0
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(
                vibrationPattern,
                0
            )
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
