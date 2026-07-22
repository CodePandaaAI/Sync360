package com.liftley.sync360.data.network.tcp

object FileTransferConstants {
    const val PAYLOAD_BUFFER_SIZE_BYTES = 512 * 1024
    const val CONNECT_TIMEOUT_MILLIS = 5_000
    const val SOCKET_TIMEOUT_MILLIS = 60_000
    const val WAITING_FOR_FIRST_FILE_TIMEOUT_MILLIS = 30_000L
}
