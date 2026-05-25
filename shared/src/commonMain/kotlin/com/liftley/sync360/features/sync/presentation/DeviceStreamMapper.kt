package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.SyncMessage

internal fun List<SyncMessage>.toDeviceStream(peerId: String): DeviceStream {
    val texts = filter { !it.isFile && !it.isFromMe }
        .takeLast(LATEST_TEXT_LIMIT)
        .asReversed()
        .map { message ->
            ClipboardEntry(
                text = message.text,
                updatedLabel = message.timestamp.toHourMinuteLabel(),
                sourceApp = "Peer",
                isFromMe = message.isFromMe
            )
        }

    return DeviceStream(
        deviceId = peerId,
        clipboard = texts.firstOrNull() ?: ClipboardEntry("", "", ""),
        media = emptyList(),
        documents = emptyList(),
        storageUsedPercent = 0,
        lastSeenLabel = "Now",
        latestTexts = texts
    )
}

private fun Long.toHourMinuteLabel(): String =
    if (this <= 0L) "" else formatTimestampHourMinute(this)

private const val LATEST_TEXT_LIMIT = 3
