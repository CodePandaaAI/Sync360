package com.liftley.sync360.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.liftley.sync360.MainActivity

class SyncService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var serviceMode: String = MODE_CONNECTED

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceMode = intent?.getStringExtra(EXTRA_MODE)?.takeIf { it.isNotBlank() } ?: MODE_CONNECTED
        acquireLocks()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireLocks() {
        releaseLocks()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sync360:WakeLock").apply {
            acquire(if (serviceMode == MODE_TRANSFER) TRANSFER_WAKE_LOCK_TIMEOUT_MS else SESSION_WAKE_LOCK_TIMEOUT_MS)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("Sync360:MulticastLock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sync360 Connection Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps connection active during background sync sessions."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (serviceMode == MODE_TRANSFER) {
            "Keeping file transfer active in background."
        } else {
            "Keeping active sharing session stable in background."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync360 Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sync360_service_channel"
        private const val NOTIFICATION_ID = 5001
        private const val EXTRA_MODE = "sync360_service_mode"
        private const val MODE_CONNECTED = "connected"
        private const val MODE_TRANSFER = "transfer"
        private const val SESSION_WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
        private const val TRANSFER_WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L
    }
}
