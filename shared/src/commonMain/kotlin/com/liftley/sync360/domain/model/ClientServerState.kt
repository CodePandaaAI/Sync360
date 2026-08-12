package com.liftley.sync360.domain.model

import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest

sealed interface ClientServerState {
    data object Idle : ClientServerState

    data class TextOffer(
        val textOffer: TextOfferRequest
    ) : ClientServerState

    data class WaitingForText(
        val textOffer: TextOfferRequest
    ) : ClientServerState

    data class TextReceived(val data: String) : ClientServerState

    data class FileOffer(
        val fileOffer: FileOfferRequest
    ) : ClientServerState

    data class WaitingForFiles(
        val fileOffer: FileOfferRequest
    ) : ClientServerState

    data class ReceivingFiles(
        val fileOffer: FileOfferRequest,
        val completedFileCount: Int,
        val progress: FileTransferProgress
    ) : ClientServerState

    data class FilesReceived(
        val senderDeviceName: String,
        val fileCount: Int
    ) : ClientServerState
}

enum class UserDecision {
    ACCEPTED, DECLINED, CANCELLED
}
