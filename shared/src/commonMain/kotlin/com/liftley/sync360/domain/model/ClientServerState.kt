package com.liftley.sync360.domain.model

sealed interface ClientServerState {
    data object Idle: ClientServerState
    sealed interface Busy : ClientServerState {
        data class TextOffer(
            val senderDeviceName: String,
            val senderDeviceId: String,
            val preview: String,
            val characterCount: Int
        ) : Busy

        data class FileOffer(
            val fileOffer: FileTransferOffer
        ) : Busy

        data class ReceivingFiles(
            val senderDeviceName: String,
            val fileCount: Int,
            val completedFileCount: Int
        ) : Busy
    }

    data class Received(val data: String): ClientServerState

    data class ReceivedFiles(
        val senderDeviceName: String,
        val fileCount: Int
    ) : ClientServerState
}

enum class UserDecision {
    IDLE, ACCEPTED, DECLINED
}
