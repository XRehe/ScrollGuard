package com.scrollguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScrollBlockerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var remainingTimeFormatted: String = ""

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "scroll_guard_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TIMER_MINUTES = "extra_timer_minutes"
        const val ACTION_TIMER_FINISHED = "com.scrollguard.TIMER_FINISHED"
        const val ACTION_TIMER_UPDATE = "com.scrollguard.TIMER_UPDATE"
        const val EXTRA_REMAINING_TIME = "extra_remaining_time"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Süresiz Koruma"))
        ScrollAccessibilityService.setEnabled(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minutes = intent?.getIntExtra(EXTRA_TIMER_MINUTES, 0) ?: 0
        
        if (minutes > 0) {
            startTimer(minutes)
        } else {
            countDownTimer?.cancel()
            remainingTimeFormatted = "Süresiz"
            updateNotification("🛡️ Süresiz Koruma Aktif")
        }

        return START_STICKY
    }

    private fun startTimer(minutes: Int) {
        countDownTimer?.cancel()
        
        val durationMs = minutes * 60 * 1000L
        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val totalMinutes = totalSeconds / 60
                val totalHours = totalMinutes / 60
                val totalDays = totalHours / 24

                val d = totalDays
                val h = totalHours % 24
                val m = totalMinutes % 60
                val s = totalSeconds % 60

                remainingTimeFormatted = if (d > 0) {
                    String.format("%d GÜN, %02d:%02d:%02d", d, h, m, s)
                } else if (h > 0) {
                    String.format("%02d:%02d:%02d", h, m, s)
                } else {
                    String.format("%02d:%02d", m, s)
                }
                
                updateNotification("⏳ Kalan: $remainingTimeFormatted")
                broadcastUpdate(remainingTimeFormatted)
            }

            override fun onFinish() {
                isRunning = false
                remainingTimeFormatted = "00:00"
                broadcastUpdate("Bitti")
                sendBroadcast(Intent(ACTION_TIMER_FINISHED))
                stopSelf()
            }
        }.start()
    }

    private fun broadcastUpdate(time: String) {
        val intent = Intent(ACTION_TIMER_UPDATE).apply {
            putExtra(EXTRA_REMAINING_TIME, time)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        isRunning = false
        ScrollAccessibilityService.setEnabled(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ScrollGuard Koruması",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ScrollGuard arka planda aktif"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ ScrollGuard Aktif")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
