package com.liftley.sync360.features.sync.domain.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class SyncDiagnosticEntry(
    val timestampMillis: Long,
    val subsystem: String,
    val stateBefore: String,
    val event: String,
    val stateAfter: String,
    val outcomeCode: String
)

@OptIn(ExperimentalTime::class)
class SyncDiagnosticLog {
    private val _entries = MutableStateFlow<List<SyncDiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<SyncDiagnosticEntry>> = _entries.asStateFlow()

    fun record(
        subsystem: String,
        stateBefore: String,
        event: String,
        stateAfter: String,
        outcomeCode: String
    ) {
        val entry = SyncDiagnosticEntry(
            timestampMillis = Clock.System.now().toEpochMilliseconds(),
            subsystem = subsystem,
            stateBefore = stateBefore,
            event = event,
            stateAfter = stateAfter,
            outcomeCode = outcomeCode
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
