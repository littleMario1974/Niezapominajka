package com.mario.niezapominajka

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Trwały, cichy baner nad innymi aplikacjami.
 * Użytkownik zamyka go przyciskiem albo przesunięciem w bok.
 */
class PersistentBannerService : Service() {

    private var windowManager: WindowManager? = null
    private var bannerView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra("reminder_text") ?: "Masz przypomnienie"
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(text))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        showBanner(text)
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trwały baner",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ciche powiadomienie podtrzymujące widoczność banera"
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Niezapominajka")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

    private fun showBanner(text: String) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        bannerView?.let { try { wm.removeView(it) } catch (_: Exception) {} }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(12))
            setBackgroundColor(Color.rgb(248, 226, 222))
            elevation = dp(10).toFloat()
        }

        val message = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.rgb(65, 35, 40))
            setPadding(0, 0, dp(8), 0)
            maxLines = 3
        }
        container.addView(
            message,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val close = Button(this).apply {
            this.text = "Zamknij"
            isAllCaps = false
            setOnClickListener { dismissBanner() }
        }
        container.addView(close)

        var downX = 0f
        var downY = 0f
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(70) && abs(dx) > abs(dy)) dismissBanner()
                    true
                }
                else -> true
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(8)
        }

        try {
            wm.addView(container, params)
            bannerView = container
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun dismissBanner() {
        bannerView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        bannerView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bannerView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        bannerView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "persistent_banner_service"
        private const val NOTIFICATION_ID = 70002
    }
}
