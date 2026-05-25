package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.SyncMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceStreamMapperTest {

    @Test
    fun mapsLatestPeerTextMessagesNewestFirst() {
        val stream = listOf(
            message("one"),
            message("ignored-from-me", isFromMe = true),
            message("ignored-file", isFile = true),
            message("two"),
            message("three"),
            message("four")
        ).toDeviceStream(peerId = "peer")

        assertEquals("four", stream.clipboard.text)
        assertEquals(listOf("four", "three", "two"), stream.latestTexts.map { it.text })
    }

    private fun message(
        text: String,
        isFromMe: Boolean = false,
        isFile: Boolean = false
    ): SyncMessage =
        SyncMessage(
            id = text,
            peerDeviceId = "peer",
            text = text,
            isFromMe = isFromMe,
            timestamp = 0L,
            isFile = isFile
        )
}
