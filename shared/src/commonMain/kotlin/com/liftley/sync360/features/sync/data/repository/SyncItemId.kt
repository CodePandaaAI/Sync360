package com.liftley.sync360.features.sync.data.repository

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun newSyncItemId(): String {
    val suffix = (1..8)
        .map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }
        .joinToString("")
    return "${Clock.System.now().toEpochMilliseconds()}-$suffix"
}
