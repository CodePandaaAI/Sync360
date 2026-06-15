package com.liftley.sync360.features.sync.domain.diagnostics

internal actual fun transferExecutionContext(): String =
    "thread=${Thread.currentThread().name}"
