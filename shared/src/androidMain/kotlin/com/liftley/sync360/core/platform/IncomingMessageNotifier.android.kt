package com.liftley.sync360.core.platform

import android.content.Context

class AndroidIncomingMessageNotifier(
    @Suppress("UNUSED_PARAMETER") context: Context
) : IncomingMessageNotifier {

    override fun notifyIncoming(senderName: String, preview: String, isFile: Boolean) {
        // Sync360 uses the foreground service notification as the single live state surface.
        // One-off Android notifications can become stale and disagree with transfer state.
    }
}

actual fun createIncomingMessageNotifier(context: Any?): IncomingMessageNotifier {
    val androidContext = context as? Context
        ?: return NoOpIncomingMessageNotifier()
    return AndroidIncomingMessageNotifier(androidContext)
}
