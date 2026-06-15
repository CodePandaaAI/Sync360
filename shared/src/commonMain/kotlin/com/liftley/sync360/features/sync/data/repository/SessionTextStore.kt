package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.SyncMessage

internal class SessionTextStore {
    private val messagesByPeerId = mutableMapOf<String, List<SyncMessage>>()

    fun messagesFor(peerId: String?): List<SyncMessage> {
        if (peerId == null) return emptyList()
        return messagesByPeerId[peerId].orEmpty()
    }

    fun addText(
        itemId: String,
        peerId: String,
        originDeviceId: String,
        localDeviceId: String,
        text: String,
        timestamp: Long
    ): List<SyncMessage> {
        val message = SyncMessage(
            id = itemId,
            peerDeviceId = peerId,
            text = text,
            isFromMe = originDeviceId == localDeviceId,
            timestamp = timestamp,
            isFile = false,
            fileName = null
        )
        val updated = messagesByPeerId[peerId].orEmpty() + message
        messagesByPeerId[peerId] = updated
        return updated
    }

    fun removePeer(peerId: String) {
        messagesByPeerId.remove(peerId)
    }
}
