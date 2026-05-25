package com.liftley.sync360.core.platform

class NoOpIncomingMessageNotifier : IncomingMessageNotifier {
    override fun notifyIncoming(senderName: String, preview: String, isFile: Boolean) = Unit
}
