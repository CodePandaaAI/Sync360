package com.liftley.sync360.features.sync.domain.usecase

import com.liftley.sync360.features.sync.domain.repository.SyncRepository

class DisconnectAllUseCase(
    private val repository: SyncRepository
) {
    operator fun invoke() = repository.disconnectAll()
}
