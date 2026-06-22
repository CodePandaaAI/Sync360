package com.liftley.sync360.features.sync.domain.usecase

import com.liftley.sync360.features.sync.domain.runtime.SyncRuntimeController

class DisconnectAllUseCase(
    private val runtimeController: SyncRuntimeController
) {
    operator fun invoke() = runtimeController.shutdown()
}
