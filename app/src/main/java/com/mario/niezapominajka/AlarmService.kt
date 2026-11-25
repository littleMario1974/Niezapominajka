package com.mario.niezapominajka

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri

/** Koordynuje dźwięk wszystkich aktualnie aktywnych przypomnień. */
class AlarmService : Service() {

    private data class ActiveAlarm(
        val id: Long,
        val text: String,
        val audioUri: String?,
        val voiceFilePath: String?
    )

    private val activeAlarms = linkedMapOf<Long, ActiveAlarm>()
    private var mediaPlayer: MediaPlayer? = null
    private var playingReminderId: Long? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
            if (reminderId != -1L) {
                activeAlarms.remove(reminderId)
                if (playingReminderId == reminderId) {
                    stopPlayer()
                    playNextActiveAlarm()
                }
            }
            if (activeAlarms.isEmpty()) {
                stopPlayer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            } else {
                updateForegroundNotification()
            }
            return START_NOT_STICKY
        }

        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId == -1L) {
            Log.e(TAG, "Odrzucono alarm bez prawidłowego ID.")
            return START_NOT_STICKY
        }

        val alarm = ActiveAlarm(
            id = reminderId,
            text = intent.getStringExtra("reminder_text") ?: "Masz przypomnienie",
            audioUri = intent.getStringExtra("audio_uri"),
            voiceFilePath = intent.getStringExtra("voice_file_path")
        )
        activeAlarms[reminderId] = alarm
        if (playingReminderId == reminderId) stopPlayer()

        createServiceChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification(alarm.text))
        if (playingReminderId == null) playNextActiveAlarm()
        return START_NOT_STICKY
    }

    private fun playNextActiveAlarm() {
        if (playingReminderId != null) return
        val nextAlarm = activeAlarms.values.firstOrNull() ?: return
        if (playAlarm(nextAlarm.audioUri, nextAlarm.voiceFilePath)) {
            playingReminderId = nextAlarm.id
        }
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
                        ?: return false
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

    private fun stopPlayer() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            } finally {
                player.release()
            }
        }
        mediaPlayer = null
        playingReminderId = null
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
        stopPlayer()
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
    }
}
