
package com.mario.niezapominajka

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createServiceChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Niezapominajka")
            .setContentText(
                intent?.getStringExtra("reminder_text")
                    ?: "Masz przypomnienie"
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val soundEnabled =
            intent?.getBooleanExtra("sound_enabled", true) ?: true
        val speakText =
            intent?.getBooleanExtra("speak_text", false) ?: false
        val audioUri = intent?.getStringExtra("audio_uri")
        val voiceFilePath = intent?.getStringExtra("voice_file_path")

        stopAlarm()

        if (soundEnabled && !speakText) {
            playAlarm(audioUri, voiceFilePath)
        }

        return START_NOT_STICKY
    }

    private fun playAlarm(
        audioUri: String?,
        voiceFilePath: String?
    ) {
        try {
            val player = MediaPlayer()

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            when {
                !audioUri.isNullOrBlank() -> {
                    player.setDataSource(this, Uri.parse(audioUri))
                }

                !voiceFilePath.isNullOrBlank() -> {
                    player.setDataSource(voiceFilePath)
                }

                else -> {
                    val uri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM
                    ) ?: RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION
                    ) ?: return

                    player.setDataSource(this, uri)
                }
            }

            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            stopAlarm()
            android.util.Log.e(
                "Niezapominajka",
                "Nie udało się uruchomić dźwięku alarmu",
                e
            )
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            } finally {
                player.release()
            }
        }
        mediaPlayer = null
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Działający alarm",
                NotificationManager.IMPORTANCE_LOW
            )

            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP =
            "com.mario.niezapominajka.STOP_ALARM"

        private const val CHANNEL_ID = "active_alarm"
        private const val NOTIFICATION_ID = 70001
    }
}
