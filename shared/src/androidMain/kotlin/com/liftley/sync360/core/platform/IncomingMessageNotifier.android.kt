package com.liftley.sync360.core.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class AndroidIncomingMessageNotifier(private val context: Context) : IncomingMessageNotifier {

    override fun notifyIncoming(senderName: String, preview: String, isFile: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val title = if (isFile) "$senderName sent a file" else "$senderName sent text"
        val text = if (isFile) preview else preview.take(200)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify((senderName + preview).hashCode(), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sync360 Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "sync360_incoming_messages"
    }
}

actual fun createIncomingMessageNotifier(context: Any?): IncomingMessageNotifier {
    val androidContext = context as? Context
        ?: return NoOpIncomingMessageNotifier()
    return AndroidIncomingMessageNotifier(androidContext)
}
