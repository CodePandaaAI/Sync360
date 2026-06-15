package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.features.sync.data.network.ConnectRequestOutcome
import com.liftley.sync360.features.sync.data.network.FileTransferManager
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.HttpSyncServer
import com.liftley.sync360.features.sync.data.network.IncomingUploadFailure
import com.liftley.sync360.features.sync.data.network.OutgoingFileTransferCoordinator
import com.liftley.sync360.features.sync.data.network.SyncServerListener
import com.liftley.sync360.features.sync.data.network.api.ConnectAcceptDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRejectDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRequestDto
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.MessageDto
import com.liftley.sync360.features.sync.domain.controller.SyncDiscoveryController
import com.liftley.sync360.features.sync.domain.model.ConnectionEvent
import com.liftley.sync360.features.sync.domain.model.ConnectionSnapshot
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.SessionSnapshot
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.model.SyncStartResult
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import com.liftley.sync360.features.sync.domain.model.UserFacingFailure
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SyncRepositoryImpl(
    private val discoveryController: SyncDiscoveryController,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = 8080,
    private val httpClient: HttpSyncClient = HttpSyncClient(syncPort),
    private val httpServer: HttpSyncServer = HttpSyncServer(syncPort),
    private val fileTransferManager: FileTransferManager =
        FileTransferManager(platformOperations, httpClient),
    private val outgoingFileTransferCoordinator: OutgoingFileTransferCoordinator =
        OutgoingFileTransferCoordinator(localDevice, httpClient, fileTransferManager)
) : SyncRepository, SyncServerListener {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runtimeLock = Any()
    private var syncStarted = false
    private var runtimeClosed = false

    private val deviceSession = DeviceSessionStore()
    private val deviceRegistry = DeviceRegistry()
    private val sessionTokens = SessionTokenStore()
    private val events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 8)
    private val sessionAuthenticator = SessionAuthenticator(
        localDevice = localDevice,
        localAddressForPeer = { peerHost ->
            platformOperations.getNetworkEnvironment().addressForPeer(peerHost)
        },
        localPort = syncPort
    )
    private val incomingFileTransferCoordinator = IncomingFileTransferCoordinator(
        fileTransferManager,
        sessionAuthenticator,
        platformOperations
    )
    private val messageEngine = MessageEngine(
        scope = scope,
        localDevice = localDevice,
        incomingNotifier = incomingNotifier,
        httpClient = httpClient,
        deviceSession = deviceSession,
        deviceRegistry = deviceRegistry,
        sessionAuthenticator = sessionAuthenticator,
        events = events
    )
    private val transferEngine = TransferEngine(
        scope = scope,
        incomingNotifier = incomingNotifier,
        platformOperations = platformOperations,
        deviceSession = deviceSession,
        deviceRegistry = deviceRegistry,
        incoming = incomingFileTransferCoordinator,
        outgoing = outgoingFileTransferCoordinator
    )
    private val connectionEngine = ConnectionEngine(
        scope = scope,
        localDevice = localDevice,
        syncPort = syncPort,
        httpClient = httpClient,
        deviceSession = deviceSession,
        deviceRegistry = deviceRegistry,
        sessionTokens = sessionTokens,
        sessionAuthenticator = sessionAuthenticator,
        events = events,
        hasActiveTransfer = { transferEngine.isActive },
        onSessionCleared = {
            messageEngine.clearVisible()
            transferEngine.cancel()
        },
        onDeviceDeleted = messageEngine::removePeer
    )

    override val nearbyDevices: Flow<List<DeviceProfile>> = discoveryController.nearbyDevices
    override val isScanning: Flow<Boolean> = discoveryController.isScanning
    override val connectionEvents: Flow<ConnectionEvent> = events.asSharedFlow()
    override val connectionSnapshot: Flow<ConnectionSnapshot> = connectionEngine.connectionSnapshot
    override val sessionSnapshot: Flow<SessionSnapshot> = connectionEngine.sessionSnapshot
    override val sessionMessages: Flow<List<SyncMessage>> = messageEngine.messages
    override val transferSnapshot: Flow<TransferSnapshot> = transferEngine.snapshot

    init {
        httpServer.listener = this
    }

    override fun startSync(): SyncStartResult {
        val decision = synchronized(runtimeLock) {
            when {
                runtimeClosed -> SyncStartResult.RUNTIME_CLOSED
                syncStarted -> SyncStartResult.ALREADY_RUNNING
                else -> {
                    syncStarted = true
                    SyncStartResult.STARTED
                }
            }
        }
        if (decision != SyncStartResult.STARTED) return decision

        if (!platformOperations.getNetworkEnvironment().isAvailable) {
            synchronized(runtimeLock) { syncStarted = false }
            return SyncStartResult.NETWORK_UNAVAILABLE
        }

        httpServer.listener = this
        if (!httpServer.start()) {
            synchronized(runtimeLock) { syncStarted = false }
            events.tryEmit(ConnectionEvent.Failed(UserFacingFailure.SERVER_UNAVAILABLE))
            return SyncStartResult.SERVER_UNAVAILABLE
        }
        return SyncStartResult.STARTED
    }

    override fun stopSync() {
        val shouldStop = synchronized(runtimeLock) {
            if (!syncStarted) {
                false
            } else {
                syncStarted = false
                true
            }
        }
        if (!shouldStop) return

        connectionEngine.cancelPending()
        transferEngine.cancel(updateService = false)
        httpServer.stop()
        platformOperations.stopService()
    }

    override fun shutdownSync() {
        synchronized(runtimeLock) {
            if (runtimeClosed) return
        }
        stopSync()
        httpClient.close()
        synchronized(runtimeLock) {
            runtimeClosed = true
            syncStarted = false
        }
    }

    override fun requestConnect(device: DeviceProfile) = connectionEngine.request(device)

    override fun requestConnectByHost(hostAddress: String) =
        connectionEngine.requestByHost(hostAddress)

    override fun confirmOutgoingConnect() = connectionEngine.confirm()

    override fun dismissOutgoingConnect() = connectionEngine.dismiss()

    override fun acceptIncomingConnect(deviceId: String) =
        connectionEngine.acceptIncoming(deviceId)

    override fun declineIncomingConnect(deviceId: String) =
        connectionEngine.declineIncoming(deviceId)

    override fun switchActiveDevice(deviceId: String) =
        connectionEngine.switchActive(deviceId)

    override fun disconnectActivePeer() = connectionEngine.disconnectActive()

    override fun disconnectAll() {
        transferEngine.cancel()
        connectionEngine.clearSession()
        shutdownSync()
    }

    override suspend fun clearAllData() = connectionEngine.clearSession()

    override fun deleteDevice(deviceId: String) = connectionEngine.deleteDevice(deviceId)

    override fun sendText(text: String) = messageEngine.send(text)

    override fun offerFiles(files: List<PickedFile>) = transferEngine.offer(files)

    override fun dismissReceivedFiles() = transferEngine.dismissReceivedFiles()

    override fun dismissTransferFailure() = transferEngine.dismissFailure()

    override fun onConnectRequest(
        request: ConnectRequestDto,
        remoteHost: String
    ): ConnectRequestOutcome = connectionEngine.onConnectRequest(request, remoteHost)

    override fun onConnectAccept(accept: ConnectAcceptDto, remoteHost: String): Boolean =
        connectionEngine.onConnectAccept(accept, remoteHost)

    override fun onConnectReject(reject: ConnectRejectDto, remoteHost: String): Boolean =
        connectionEngine.onConnectReject(reject, remoteHost)

    override fun onTextMessage(message: MessageDto, remoteHost: String): Boolean =
        messageEngine.receive(message, remoteHost)

    override fun onFileOffer(offer: FileOfferDto, remoteHost: String): Boolean =
        transferEngine.receiveOffer(offer, remoteHost)

    override fun onFileComplete(complete: FileCompleteDto, remoteHost: String): Boolean =
        transferEngine.receiveComplete(complete, remoteHost)

    override fun onIncomingFileChunkInit(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields,
        declaredLength: Long,
        remoteHost: String
    ): Boolean = transferEngine.initIncomingFile(
        offerId,
        fileIndex,
        sessionToken,
        authFields,
        declaredLength,
        remoteHost
    )

    override fun onIncomingFileChunkReceived(
        offerId: String,
        fileIndex: Int,
        chunk: ByteArray
    ): Boolean = transferEngine.receiveChunk(offerId, fileIndex, chunk)

    override fun onIncomingFileChunkComplete(offerId: String, fileIndex: Int): String? =
        transferEngine.completeIncomingFile(offerId, fileIndex)

    override fun onIncomingFileChunkError(
        offerId: String,
        fileIndex: Int,
        knownFailure: IncomingUploadFailure?
    ): IncomingUploadFailure? =
        transferEngine.failIncomingFile(offerId, fileIndex, knownFailure)

    override fun consumeIncomingFileFailure(
        offerId: String,
        fileIndex: Int
    ): IncomingUploadFailure? = transferEngine.consumeIncomingFailure(offerId, fileIndex)
}
