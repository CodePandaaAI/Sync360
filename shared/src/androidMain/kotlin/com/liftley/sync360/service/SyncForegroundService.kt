package com.liftley.sync360.service

import android.app.*
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.liftley.sync360.core.network.SyncClient
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Android Foreground Service that maintains a persistent WebSocket connection
 * using [SyncClient] for background clipboard synchronization.
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

        /** Intent extra key for the optional port number. */
        const val EXTRA_PORT = "extra_port"

        /** Default WebSocket port. */
        private const val DEFAULT_PORT = 8080

        /** Reference to the SyncClient so the overlay can send messages through it. */
        var syncClient: SyncClient? = null
            private set
    }

    /** Dedicated coroutine scope for the service lifecycle. */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** The [SyncClient] managing the WebSocket connection. */
    private lateinit var client: SyncClient

    /** System notification manager for updating the persistent notification. */
    private lateinit var notificationManager: NotificationManager

    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Retrieve SyncClient singleton from Koin
        val koin = org.koin.mp.KoinPlatformTools.defaultContext().get()
        client = koin.get()
        syncClient = client

        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()
                    if (!text.isNullOrBlank()) {
                        if (client.connectionStatus.value == ConnectionStatus.CONNECTED) {
                            val sharedPrefs = getSharedPreferences("sync360_prefs", MODE_PRIVATE)
                            var deviceId = sharedPrefs.getString("device_uuid", null)
                            if (deviceId == null) {
                                deviceId = java.util.UUID.randomUUID().toString()
                                sharedPrefs.edit { putString("device_uuid", deviceId) }
                            }
                            val model = listOf(Build.MANUFACTURER, Build.MODEL)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { "Android device" }

                            val payload = com.liftley.sync360.core.network.SyncPayload(
                                kind = "clipboard",
                                originDeviceId = "android-$deviceId",
                                originDeviceName = model,
                                originDeviceType = com.liftley.sync360.features.sync.domain.model.DeviceType.PHONE.name,
                                content = text,
                                timestamp = System.currentTimeMillis()
                            )
                            client.sendFrame(com.liftley.sync360.core.network.SyncPayloadCodec.encode(payload))
                        }
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
        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)

        val sharedPrefs = getSharedPreferences("sync360_prefs", MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("device_uuid", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            sharedPrefs.edit { putString("device_uuid", deviceId) }
        }
        val deviceIdStr = "android-$deviceId"

        // Promote to foreground immediately with an initial "Standby" notification.
        startForeground(NOTIFICATION_ID, buildNotification("Standby — waiting to connect…"))

        // Connect the WebSocket client to the target host.
        client.connect(hostIp, port, deviceIdStr)

        // Observe connection status and update the notification accordingly.
        serviceScope.launch {
            client.connectionStatus.collect { status ->
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
        client.disconnect()
        syncClient = null
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

        val iconId = resources.getIdentifier("ic_launcher", "mipmap", packageName).takeIf { it != 0 }
            ?: android.R.drawable.ic_menu_share

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
