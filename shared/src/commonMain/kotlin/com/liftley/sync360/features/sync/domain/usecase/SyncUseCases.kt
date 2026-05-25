package com.liftley.sync360.features.sync.domain.usecase

import com.liftley.sync360.core.network.SyncPayload
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow

class ObserveDevicesUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<List<DeviceProfile>> = repository.devices
}

class ObserveConnectionStatusUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<ConnectionStatus> = repository.connectionStatus
}

class ObserveActiveDeviceIdUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<String?> = repository.activeDeviceId
}

class ObserveRecentPayloadsUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<List<SyncPayload>> = repository.recentPayloads
}

class StartDiscoveryUseCase(private val repository: SyncRepository) {
    operator fun invoke() {
        repository.startDiscovery()
        repository.startServer()
    }
}

class ConnectToDeviceUseCase(private val repository: SyncRepository) {
    operator fun invoke(device: DeviceProfile) = repository.connectToDevice(device)
}

class SendTextUseCase(private val repository: SyncRepository) {
    operator fun invoke(text: String) {
        if (text.isNotBlank()) {
            repository.sendText(text)
        }
    }
}

class DisconnectAllUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.disconnectAll()
}
