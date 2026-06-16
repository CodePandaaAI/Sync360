package com.liftley.sync360.features.sync.domain.controller

import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.repository.SyncRepository

class SyncTransferController(
    private val repository: SyncRepository
) {
    fun send(files: List<PickedFile>) = repository.offerFiles(files)

    fun dismissReceived() = repository.dismissReceivedFiles()

    fun dismissFailure() = repository.dismissTransferFailure()

    fun cancel() = repository.cancelTransfer()

}
