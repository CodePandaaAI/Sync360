package com.liftley.sync360.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.liftley.sync360.MainActivity

class SyncService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_READY
        if (mode == MODE_TRANSFERRING) {
            acquireTransferWakeLock()
        } else {
            releaseWakeLock()
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(intent, mode))
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireTransferWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Sync360:TransferWakeLock"
        ).apply {
            acquire(TRANSFER_WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseLocks() {
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sync360 File Transfers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Sync360 ready for local transfers."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(intent: Intent?, mode: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
package com.liftley.sync360.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.liftley.sync360.MainActivity

class SyncService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_READY
        if (mode == MODE_TRANSFERRING) {
            acquireTransferWakeLock()
        } else {
            releaseWakeLock()
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(intent, mode))
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireTransferWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Sync360:TransferWakeLock"
        ).apply {
            acquire(TRANSFER_WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseLocks() {
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sync360 File Transfers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Sync360 ready for local transfers."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(intent: Intent?, mode: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME)
        val detail = intent?.getStringExtra(EXTRA_DETAIL)
        val fileCount = intent?.getIntExtra(EXTRA_FILE_COUNT, 0) ?: 0
        val progressPercent = intent?.getIntExtra(EXTRA_PROGRESS, -1)?.takeIf { it >= 0 }
        val title = when (mode) {
            MODE_TRANSFERRING -> transferTitle(peerName, fileCount)
            MODE_ERROR -> "Transfer failed"
            else -> "Sync360 is ready"
        }
        val text = when {
            mode == MODE_TRANSFERRING && progressPercent != null ->
                "${detail ?: "Transferring files"} - $progressPercent%"
            mode == MODE_ERROR -> detail ?: "Open Sync360 to send or receive."
            else -> detail ?: "Ready to receive on your local network."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(mode != MODE_ERROR)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (mode == MODE_TRANSFERRING && progressPercent != null) {
                    setProgress(100, progressPercent, false)
                }
            }
            .build()
    }

    private fun transferTitle(peerName: String?, fileCount: Int): String {
        val action = if (fileCount <= 1) "Transferring file" else "Transferring $fileCount files"
        return peerName?.let { "$action with $it" } ?: action
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_FILE_COUNT = "file_count"
        const val MODE_READY = "ready"
        const val MODE_TRANSFERRING = "transferring"
        const val MODE_ERROR = "error"
        private const val CHANNEL_ID = "sync360_service_channel"
        private const val NOTIFICATION_ID = 5001
        private const val TRANSFER_WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L
    }
}
