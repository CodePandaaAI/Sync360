package com.liftley.sync360.features.sync.domain.usecase

import com.liftley.sync360.features.sync.domain.repository.SyncRepository

class DeleteDeviceUseCase(
    private val repository: SyncRepository
) {
    suspend operator fun invoke(deviceId: String) {
        repository.deleteDevice(deviceId)
    }
}
