package com.liftley.sync360.features.sync.domain.usecase

import com.liftley.sync360.features.sync.domain.repository.SyncRepository

class ClearAllDataUseCase(
    private val repository: SyncRepository
) {
    suspend operator fun invoke() {
        repository.clearAllData()
    }
}
