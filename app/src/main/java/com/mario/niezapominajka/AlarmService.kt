package com.mario.niezapominajka

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import java.util.Locale

/** Koordynuje dźwięk i odczytywanie wszystkich aktywnych przypomnień. */
class AlarmService : Service() {

    private data class ActiveAlarm(
        val id: Long,
        val text: String,
        val audioUri: String?,
        val voiceFilePath: String?,
        val soundEnabled: Boolean,
        val speakText: Boolean
    )

    private val activeAlarms = linkedMapOf<Long, ActiveAlarm>()
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitializing = false
    private var currentReminderId: Long? = null
    private var speechRepeatRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandIntent = intent ?: return START_NOT_STICKY

        if (commandIntent.action == ACTION_STOP) {
            val reminderId = commandIntent.getLongExtra(EXTRA_REMINDER_ID, -1L)
            if (reminderId != -1L) {
                activeAlarms.remove(reminderId)
                if (currentReminderId == reminderId) {
                    stopCurrentPlayback()
                    playNextActiveAlarm()
                }
            }
            if (activeAlarms.isEmpty()) {
                stopCurrentPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            } else {
                updateForegroundNotification()
            }
            return START_NOT_STICKY
        }

        val reminderId = commandIntent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) {
            Log.e(TAG, "Odrzucono alarm bez prawidłowego ID.")
            return START_NOT_STICKY
        }

        val alarm = ActiveAlarm(
            id = reminderId,
            text = commandIntent.getStringExtra("reminder_text") ?: "Masz przypomnienie",
            audioUri = commandIntent.getStringExtra("audio_uri"),
            voiceFilePath = commandIntent.getStringExtra("voice_file_path"),
            soundEnabled = commandIntent.getBooleanExtra("sound_enabled", true),
            speakText = commandIntent.getBooleanExtra("speak_text", false)
        )
        activeAlarms[reminderId] = alarm

        createServiceChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification(alarm.text))
        if (currentReminderId == null) playNextActiveAlarm()
        return START_NOT_STICKY
    }

    private fun playNextActiveAlarm() {
        if (currentReminderId != null) return

        val nextAlarm = activeAlarms.values.firstOrNull() ?: return
        currentReminderId = nextAlarm.id

        if (nextAlarm.speakText) {
            ensureTextToSpeech()
            if (ttsReady) speakCurrentAlarm()
        } else if (nextAlarm.soundEnabled) {
            if (!playAlarm(nextAlarm.audioUri, nextAlarm.voiceFilePath)) {
                failCurrentAlarm()
            }
        } else {
            failCurrentAlarm()
        }
    }

    private fun ensureTextToSpeech() {
        if (ttsReady || ttsInitializing) return
        ttsInitializing = true
        textToSpeech = TextToSpeech(applicationContext) { status ->
            ttsInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                val languageResult = textToSpeech?.setLanguage(
                    Locale.forLanguageTag("pl-PL")
                )
                ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED

                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            handler.post {
                                val activeId = currentReminderId ?: return@post
                                if (utteranceId == "reminder_$activeId" &&
                                    activeAlarms[activeId]?.speakText == true
                                ) {
                                    scheduleSpeechRepeat(activeId)
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            handler.post {
                                val activeId = currentReminderId ?: return@post
                                if (utteranceId == "reminder_$activeId") {
                                    fallbackFromSpeech()
                                }
                            }
                        }
                    }
                )

                if (ttsReady) {
                    speakCurrentAlarm()
                } else {
                    Log.w(TAG, "Brak polskiego głosu TTS; próba użycia dźwięku alarmu.")
                    fallbackFromSpeech()
                }
            } else {
                Log.e(TAG, "Nie udało się zainicjalizować TTS; próba użycia dźwięku alarmu.")
                fallbackFromSpeech()
            }
        }
    }

    private fun speakCurrentAlarm() {
        val alarm = currentReminderId?.let(activeAlarms::get) ?: return
        if (!ttsReady || !alarm.speakText) return
        val result = textToSpeech?.speak(
            alarm.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "reminder_${alarm.id}"
        )
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "Nie udało się odczytać przypomnienia ID=${alarm.id}; próba użycia dźwięku alarmu.")
            fallbackFromSpeech()
        }
    }

    private fun scheduleSpeechRepeat(reminderId: Long) {
        speechRepeatRunnable?.let(handler::removeCallbacks)
        speechRepeatRunnable = Runnable {
            if (currentReminderId == reminderId &&
                activeAlarms[reminderId]?.speakText == true
            ) {
                speakCurrentAlarm()
            }
        }
        speechRepeatRunnable?.let { handler.postDelayed(it, SPEECH_REPEAT_DELAY_MS) }
    }

    private fun playAlarm(audioUri: String?, voiceFilePath: String?): Boolean {
        var player: MediaPlayer? = null
        return try {
            player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            when {
                !audioUri.isNullOrBlank() -> player.setDataSource(this, audioUri.toUri())
                !voiceFilePath.isNullOrBlank() -> player.setDataSource(voiceFilePath)
                else -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    if (uri == null) {
                        player.release()
                        return false
                    }
                    player.setDataSource(this, uri)
                }
            }
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
            true
        } catch (e: Exception) {
            try { player?.release() } catch (_: Exception) {}
            Log.e(TAG, "Nie udało się uruchomić dźwięku alarmu.", e)
            false
        }
    }

    /** Gdy TTS nie może mówić, zachowujemy alarm przez odtworzenie dźwięku, jeśli jest włączony. */
    private fun fallbackFromSpeech() {
        val alarm = currentReminderId?.let(activeAlarms::get) ?: return
        speechRepeatRunnable?.let(handler::removeCallbacks)
        speechRepeatRunnable = null
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }

        if (alarm.soundEnabled && playAlarm(alarm.audioUri, alarm.voiceFilePath)) {
            return
        }
        failCurrentAlarm()
    }

    private fun failCurrentAlarm() {
        currentReminderId?.let(activeAlarms::remove)
        stopCurrentPlayback()
        if (activeAlarms.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            playNextActiveAlarm()
        }
    }

    private fun stopCurrentPlayback() {
        speechRepeatRunnable?.let(handler::removeCallbacks)
        speechRepeatRunnable = null
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            } finally {
                player.release()
            }
        }
        mediaPlayer = null
        currentReminderId = null
    }

    private fun updateForegroundNotification() {
        val text = activeAlarms.values.firstOrNull()?.text ?: "Aktywny alarm"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    private fun buildForegroundNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Niezapominajka")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

    private fun createServiceChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Działający alarm",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopCurrentPlayback()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        activeAlarms.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.mario.niezapominajka.STOP_ALARM"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val TAG = "Niezapominajka"
        private const val CHANNEL_ID = "active_alarm"
        private const val NOTIFICATION_ID = 70001
        private const val SPEECH_REPEAT_DELAY_MS = 5_000L
    }
}
