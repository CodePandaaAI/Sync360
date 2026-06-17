package com.liftley.sync360.features.sync.domain.controller

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.repository.SyncRepository

class SyncConnectionController(
    private val repository: SyncRepository
) {
    fun request(device: DeviceProfile) = repository.requestConnect(device)

    fun requestByHost(hostAddress: String) = repository.requestConnectByHost(hostAddress)

    fun confirmRequest() = repository.confirmOutgoingConnect()

    fun dismissRequest() = repository.dismissOutgoingConnect()

    fun acceptIncoming(deviceId: String) = repository.acceptIncomingConnect(deviceId)

    fun declineIncoming(deviceId: String) = repository.declineIncomingConnect(deviceId)
}
