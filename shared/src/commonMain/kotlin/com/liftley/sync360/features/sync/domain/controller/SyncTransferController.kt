package com.liftley.sync360.features.sync.domain.controller

import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.repository.SyncRepository

class SyncTransferController(
    private val repository: SyncRepository
) {

    fun sendItemsTo(deviceId: String, items: List<SendItem>) =
        repository.offerItemsTo(deviceId, items)

    fun sendItemsToHost(hostAddress: String, items: List<SendItem>) =
        repository.offerItemsToHost(hostAddress, items)

    fun dismissReceived() = repository.dismissReceivedFiles()

    fun dismissFailure() = repository.dismissTransferFailure()

    fun cancel() = repository.cancelTransfer()

}
