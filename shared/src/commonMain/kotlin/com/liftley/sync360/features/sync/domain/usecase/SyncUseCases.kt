package com.liftley.sync360.features.sync.domain.usecase

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.IncomingFileOffer
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow

class ObservePairedDevicesUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<List<DeviceProfile>> = repository.pairedDevices
}

class ObserveNearbyDevicesUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<List<DeviceProfile>> = repository.nearbyDevices
}

class ObservePendingIncomingConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<List<DeviceProfile>> = repository.pendingIncomingConnectRequests
}

class ObservePendingOutgoingConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<DeviceProfile?> = repository.pendingOutgoingConnectDevice
}

class ObserveConnectionStatusUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<ConnectionStatus> = repository.connectionStatus
}

class ObserveActiveDeviceIdUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<String?> = repository.activeDeviceId
}

class ObserveConversationMessagesUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<List<SyncMessage>> = repository.conversationMessages
}

class ObserveIncomingFileOfferUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<IncomingFileOffer?> = repository.incomingFileOffer
}

class ObserveReceivedFileBatchUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<ReceivedFileBatch?> = repository.receivedFileBatch
}

class ObserveFileTransferProgressUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<FileTransferProgress?> = repository.fileTransferProgress
}

class StartSyncUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.startSync()
}

class RequestConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke(device: DeviceProfile) = repository.requestConnect(device)
}

class ConfirmOutgoingConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.confirmOutgoingConnect()
}

class DismissOutgoingConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.dismissOutgoingConnect()
}

class AcceptIncomingConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke(deviceId: String) = repository.acceptIncomingConnect(deviceId)
}

class DeclineIncomingConnectUseCase(private val repository: SyncRepository) {
    operator fun invoke(deviceId: String) = repository.declineIncomingConnect(deviceId)
}

class SwitchActiveDeviceUseCase(private val repository: SyncRepository) {
    operator fun invoke(deviceId: String) = repository.switchActiveDevice(deviceId)
}

class SendTextUseCase(private val repository: SyncRepository) {
    operator fun invoke(text: String) {
        if (text.isNotBlank()) repository.sendText(text)
    }
}

class OfferFilesUseCase(private val repository: SyncRepository) {
    operator fun invoke(files: List<PickedFile>) {
        if (files.isNotEmpty()) repository.offerFiles(files)
    }
}

class AcceptFileOfferUseCase(private val repository: SyncRepository) {
    operator fun invoke(offerId: String) = repository.acceptFileOffer(offerId)
}

class DeclineFileOfferUseCase(private val repository: SyncRepository) {
    operator fun invoke(offerId: String) = repository.declineFileOffer(offerId)
}

class DismissReceivedFilesUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.dismissReceivedFiles()
}

class DisconnectActivePeerUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.disconnectActivePeer()
}

class DisconnectAllUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.disconnectAll()
}

class ObserveIsScanningUseCase(private val repository: SyncRepository) {
    operator fun invoke(): Flow<Boolean> = repository.isScanning
}

class TriggerManualScanUseCase(private val repository: SyncRepository) {
    operator fun invoke() = repository.triggerManualScan()
}
