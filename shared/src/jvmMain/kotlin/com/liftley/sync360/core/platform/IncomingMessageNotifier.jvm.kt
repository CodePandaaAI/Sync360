package com.liftley.sync360.core.platform

actual fun createIncomingMessageNotifier(context: Any?): IncomingMessageNotifier = NoOpIncomingMessageNotifier()
