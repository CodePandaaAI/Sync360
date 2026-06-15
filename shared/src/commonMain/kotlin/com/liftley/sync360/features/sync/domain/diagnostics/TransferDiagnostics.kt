package com.liftley.sync360.features.sync.domain.diagnostics

import kotlin.math.round

internal object TransferDiagnostics {
    fun log(
        stage: String,
        bytes: Long,
        elapsedNanos: Long,
        bufferBytes: Int,
        dispatcher: String,
        streamed: Boolean,
        fullFileInMemory: Boolean,
        base64: Boolean,
        stringEncoding: Boolean,
        json: Boolean,
        multipart: Boolean,
        executionContext: String = transferExecutionContext(),
        details: String = ""
    ) {
        val elapsedMillis = elapsedNanos.coerceAtLeast(0L) / NANOS_PER_MILLISECOND.toDouble()
        val megabytesPerSecond = if (elapsedNanos > 0L) {
            (bytes.toDouble() / BYTES_PER_MEGABYTE) /
                (elapsedNanos.toDouble() / NANOS_PER_SECOND)
        } else {
            0.0
        }
        val suffix = details.trim().takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
        println(
            "SYNC360_TRANSFER" +
                " stage=${stage.logValue()}" +
                " bytes=$bytes" +
                " elapsedMs=${elapsedMillis.twoDecimals()}" +
                " MBps=${megabytesPerSecond.twoDecimals()}" +
                " bufferBytes=$bufferBytes" +
                " dispatcher=${dispatcher.logValue()}" +
                " execution=${executionContext.logValue()}" +
                " streamed=$streamed" +
                " fullFileInMemory=$fullFileInMemory" +
                " base64=$base64" +
                " string=$stringEncoding" +
                " json=$json" +
                " multipart=$multipart" +
                suffix
        )
    }

    private fun String.logValue(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun Double.twoDecimals(): Double = round(this * 100.0) / 100.0

    private const val BYTES_PER_MEGABYTE = 1_000_000.0
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
}

internal expect fun transferExecutionContext(): String
