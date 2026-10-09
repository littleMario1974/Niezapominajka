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
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var textToSpeech: TextToSpeech? = null
    private val speechHandler = Handler(Looper.getMainLooper())
    private var speechRunnable: Runnable? = null
    private var reminderSpeechText: String = ""
    private var speechEnabled: Boolean = false

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

        val speakText =
            intent.getBooleanExtra("speak_text", false)

        speechEnabled = speakText
        reminderSpeechText = reminderText

        if (speakText) {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val languageResult = textToSpeech?.setLanguage(
                        Locale("pl", "PL")
                    )

                    if (
                        languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        textToSpeech?.setOnUtteranceProgressListener(
                            object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}

                                override fun onDone(utteranceId: String?) {
                                    speechHandler.post {
                                        scheduleSpeechAgain()
                                    }
                                }

                                override fun onError(utteranceId: String?) {
                                    speechHandler.post {
                                        scheduleSpeechAgain()
                                    }
                                }
                            }
                        )

                        speakReminder()
                    }
                }
            }
        }
        val voiceFilePath =
            intent.getStringExtra("voice_file_path")

        val audioUri =
            intent.getStringExtra("audio_uri")

        startAlarm(
            soundEnabled && !speakText,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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


    private fun speakReminder() {
        textToSpeech?.speak(
            reminderSpeechText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "reminder_speech"
        )
    }

    private fun scheduleSpeechAgain() {
        if (!speechEnabled) return

        speechRunnable?.let {
            speechHandler.removeCallbacks(it)
        }

        speechRunnable = Runnable {
            if (speechEnabled) {
                speakReminder()
            }
        }

        speechRunnable?.let {
            speechHandler.postDelayed(it, 5000)
        }
    }
    private fun startAlarm(
        soundEnabled: Boolean,
        voiceFilePath: String?,
        audioUri: String?
    ) {
        if (soundEnabled) {
            val customSoundStarted = try {
                when {
                    !audioUri.isNullOrEmpty() -> {
                        playSound {
                            setDataSource(
                                this@AlarmActivity,
                                android.net.Uri.parse(audioUri)
                            )
                        }
                        true
                    }

                    !voiceFilePath.isNullOrEmpty() -> {
                        playSound {
                            setDataSource(voiceFilePath)
                        }
                        true
                    }

                    else -> false
                }
            } catch (e: Exception) {
                mediaPlayer?.release()
                mediaPlayer = null
                false
            }

            if (!customSoundStarted) {
                try {
                    val alarmUri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM
                    ) ?: RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION
                    )

                    if (alarmUri != null) {
                        playSound {
                            setDataSource(this@AlarmActivity, alarmUri)
                        }
                    }
                } catch (e: Exception) {
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }
        }

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

    private fun playSound(
        setSource: MediaPlayer.() -> Unit
    ) {
        val player = MediaPlayer()

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            player.setSource()
            player.isLooping = true
            player.prepare()
            player.start()

            mediaPlayer = player
        } catch (e: Exception) {
            player.release()
            throw e
        }
    }

    private fun stopAlarm() {
        speechEnabled = false

        speechRunnable?.let {
            speechHandler.removeCallbacks(it)
        }
        speechRunnable = null
        speechHandler.removeCallbacksAndMessages(null)

        textToSpeech?.stop()

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: IllegalStateException) {
            } finally {
                player.release()
            }
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopAlarm()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}

