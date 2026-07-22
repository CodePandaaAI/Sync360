package com.liftley.sync360.domain.model

import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest

sealed interface ClientServerState {
    data object Idle : ClientServerState

    data class TextOffer(
        val senderDeviceName: String,
        val senderDeviceId: String,
        val preview: String,
        val characterCount: Int
    ) : ClientServerState

    data class FileOffer(
        val fileOffer: FileOfferRequest
    ) : ClientServerState

    data class ReceivingFiles(
        val senderDeviceName: String,
        val fileCount: Int,
        val completedFileCount: Int,
        val progress: FileTransferProgress
    ) : ClientServerState

    data class ReceivedText(val data: String) : ClientServerState

    data class ReceivedFiles(
        val senderDeviceName: String,
        val fileCount: Int
    ) : ClientServerState
}

enum class UserDecision {
    ACCEPTED, DECLINED
}
