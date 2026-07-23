package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.domain.model.FileTransferProgress

interface FileTransferSender {
    suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        files: List<SelectedFile>,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit,
        onProgress: (FileTransferProgress) -> Unit
    ): Result<Unit>

    fun cancelCurrentTransfer()
}
