package com.liftley.sync360.service

import android.app.*
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Android Foreground Service that maintains a persistent WebSocket connection
 * using [SyncRepository] for background clipboard synchronization.
 *
 * The service displays a persistent notification showing the current connection
 * status (Standby → Syncing → Synced) and collects incoming messages into a
 * companion object [StateFlow] so the floating overlay can read them.
 */
class SyncForegroundService : Service() {

    companion object {
        /** Notification channel ID for the foreground service. */
        private const val CHANNEL_ID = "sync360_background_sync"

        /** Notification ID for the persistent foreground notification. */
        private const val NOTIFICATION_ID = 1

        /** Intent extra key for passing the target host IP address. */
        const val EXTRA_HOST_IP = "extra_host_ip"

        /** Reference to the SyncRepository so the overlay can send messages through it. */
        var syncRepository: SyncRepository? = null
            private set
    }

    /** Dedicated coroutine scope for the service lifecycle. */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** The [SyncRepository] managing the WebSocket connection. */
    private lateinit var repository: SyncRepository

    /** System notification manager for updating the persistent notification. */
    private lateinit var notificationManager: NotificationManager

    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Retrieve SyncRepository singleton from Koin
        val koin = org.koin.mp.KoinPlatformTools.defaultContext().get()
        repository = koin.get()
        syncRepository = repository

        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()
                    if (!text.isNullOrBlank()) {
                        repository.sendText(text)
                    }
                }
            } catch (e: SecurityException) {
                // Gracefully catch background clipboard restrictions on Android 10+
                e.printStackTrace()
            }
        }
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hostIp = intent?.getStringExtra(EXTRA_HOST_IP) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately with an initial "Standby" notification.
        startForeground(NOTIFICATION_ID, buildNotification("Standby — waiting to connect…"))

        // Connect the WebSocket client to the target host.
        repository.connectToDevice(com.liftley.sync360.features.sync.domain.model.DeviceProfile(
            id = "target",
            name = "Target Device",
            type = com.liftley.sync360.features.sync.domain.model.DeviceType.DESKTOP,
            isOnline = true,
            hostAddress = hostIp
        ))

        // Observe connection status and update the notification accordingly.
        serviceScope.launch {
            repository.connectionStatus.collect { status ->
                val text = when (status) {
                    ConnectionStatus.DISCONNECTED -> "Standby — disconnected"
                    ConnectionStatus.CONNECTING   -> "Syncing — connecting to $hostIp…"
                    ConnectionStatus.CONNECTED    -> "Synced — connected to $hostIp"
                }
                notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardListener?.let {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(it)
        }

        // Gracefully disconnect the WebSocket and clean up coroutines.
        repository.disconnectAll()
        syncRepository = null
        serviceScope.cancel()

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync360 Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the clipboard sync connection alive in the background."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        // Intent to reopen the main activity (resolved by class name string to avoid compile dependency)
        val tapIntent = Intent().setClassName(this, "com.liftley.sync360.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Using standard system icon to avoid reflection warnings, 
        // real app should use R.drawable.ic_notification
        val iconId = android.R.drawable.stat_notify_sync

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync360")
            .setContentText(statusText)
            .setSmallIcon(iconId)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
