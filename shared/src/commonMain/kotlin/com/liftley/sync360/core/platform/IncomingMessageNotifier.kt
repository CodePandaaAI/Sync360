package com.liftley.sync360.core.platform

interface IncomingMessageNotifier {
    fun notifyIncoming(senderName: String, preview: String, isFile: Boolean)
}

expect fun createIncomingMessageNotifier(context: Any? = null): IncomingMessageNotifier
