package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.SessionCrypto
import com.liftley.sync360.features.sync.data.network.ConnectRequestOutcome
import com.liftley.sync360.features.sync.data.network.FileTransferManager
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.HttpSyncServer
import com.liftley.sync360.features.sync.data.network.IncomingUploadFailure
import com.liftley.sync360.features.sync.data.network.OutgoingFileTransferCoordinator
import com.liftley.sync360.features.sync.data.network.RawTcpFileHeader
import com.liftley.sync360.features.sync.data.network.RawTcpFileListener
import com.liftley.sync360.features.sync.data.network.RawTcpFileTransferConfig
import com.liftley.sync360.features.sync.data.network.RawTcpFileTransport
import com.liftley.sync360.features.sync.data.network.RawTcpFailure
import com.liftley.sync360.features.sync.data.network.RawTcpListenerStartResult
import com.liftley.sync360.features.sync.data.network.RawTcpReceiveResult
import com.liftley.sync360.features.sync.data.network.RawTransferGrantStore
import com.liftley.sync360.features.sync.data.network.SyncServerListener
import com.liftley.sync360.features.sync.data.network.api.ConnectAcceptDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRejectDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRequestDto
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferResponseDto
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
    private val rawTcpFileTransport: RawTcpFileTransport = RawTcpFileTransport(),
    private val fileTransferManager: FileTransferManager =
        FileTransferManager(platformOperations),
    private val outgoingFileTransferCoordinator: OutgoingFileTransferCoordinator =
        OutgoingFileTransferCoordinator(
            localDevice,
            httpClient,
            fileTransferManager,
            rawTcpFileTransport
        )
) : SyncRepository, SyncServerListener, RawTcpFileListener {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runtimeLock = Any()
    private var syncStarted = false
    private var runtimeClosed = false
    private var activeRawTransferId: String? = null
    private val rawTransferGrants = RawTransferGrantStore()

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
        outgoing = outgoingFileTransferCoordinator,
        onIncomingTerminated = ::closeActiveRawTransfer
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
            closeActiveRawTransfer()
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
        rawTcpFileTransport.listener = this
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
        rawTcpFileTransport.listener = this
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
        rawTcpFileTransport.stop()
        rawTransferGrants.clear()
        activeRawTransferId = null
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
        val route = connectionEngine.activePeerRoute()
        if (route != null) {
            val activeId = connectionEngine.activePeerId()
            val token = activeId?.let { deviceRegistry.sessionTokenFor(it) }
            kotlinx.coroutines.runBlocking {
                try {
                    val rejectDto = sessionAuthenticator.connectReject(token)
                    httpClient.sendConnectReject(route.host, route.port, rejectDto)
                } catch (_: Exception) {}
            }
        }
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

    override fun onFileOffer(
        offer: FileOfferDto,
        remoteHost: String
    ): FileOfferResponseDto {
        if (!transferEngine.receiveOffer(offer, remoteHost)) {
            return FileOfferResponseDto(
                accepted = false,
                failureReason = "raw_tcp_receiver_unavailable"
            )
        }
        rawTcpFileTransport.listener = this
        val listener = rawTcpFileTransport.startDynamic()
        if (listener is RawTcpListenerStartResult.Failure) {
            transferEngine.cancel()
            return FileOfferResponseDto(
                accepted = false,
                failureReason = listener.reason.logCode()
            )
        }
        val port = (listener as RawTcpListenerStartResult.Success).port
        val transferToken = SessionCrypto.secureToken()
        rawTransferGrants.register(offer.offerId, transferToken, offer.files.size)
        activeRawTransferId = offer.offerId
        return FileOfferResponseDto(
            accepted = true,
            rawTcpHost = platformOperations.getNetworkEnvironment().addressForPeer(remoteHost),
            rawTcpPort = port,
            transferId = offer.offerId,
            transferToken = transferToken
        )
    }

    override fun onFileComplete(complete: FileCompleteDto, remoteHost: String): Boolean {
        val accepted = transferEngine.receiveComplete(complete, remoteHost)
        if (accepted) closeRawTransfer(complete.offerId)
        return accepted
    }

    override fun onRawFileInit(
        header: RawTcpFileHeader,
        remoteHost: String
    ): RawTcpReceiveResult {
        if (
            header.transferId != activeRawTransferId ||
            !rawTransferGrants.validateAndConsume(
                header.transferId,
                header.transferToken,
                header.fileIndex
            )
        ) {
            return RawTcpReceiveResult.Failure(RawTcpFailure.TOKEN_INVALID)
        }
        val initialized = transferEngine.initIncomingRawFile(
            offerId = header.transferId,
            fileIndex = header.fileIndex,
            declaredLength = header.contentLength,
            fileIdentifier = header.fileIdentifier,
            remoteHost = remoteHost
        )
        if (initialized) return RawTcpReceiveResult.Success
        val incomingFailure = transferEngine.consumeIncomingFailure(
            header.transferId,
            header.fileIndex
        )
        transferEngine.failIncomingFile(
            header.transferId,
            header.fileIndex,
            incomingFailure
        )
        rawTransferGrants.revoke(header.transferId)
        activeRawTransferId = null
        return RawTcpReceiveResult.Failure(
            when (incomingFailure) {
                IncomingUploadFailure.STORAGE_FULL -> RawTcpFailure.RECEIVER_STORAGE_FULL
                IncomingUploadFailure.STORAGE_UNAVAILABLE ->
                    RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE
                IncomingUploadFailure.WRITE_FAILED -> RawTcpFailure.RECEIVER_WRITE_FAILED
                IncomingUploadFailure.INTEGRITY -> RawTcpFailure.HASH_MISMATCH
                IncomingUploadFailure.INVALID_REQUEST,
                null -> RawTcpFailure.RECEIVER_UNAVAILABLE
            }
        )
    }

    override fun onRawFileChunk(
        offerId: String,
        fileIndex: Int,
        chunk: ByteArray,
        offset: Int,
        length: Int
    ): Boolean = transferEngine.receiveChunk(offerId, fileIndex, chunk, offset, length)

    override fun onRawFileComplete(offerId: String, fileIndex: Int): RawTcpReceiveResult {
        if (transferEngine.completeIncomingFile(offerId, fileIndex) != null) {
            return RawTcpReceiveResult.Success
        }
        val failure = transferEngine.consumeIncomingFailure(offerId, fileIndex)
        return RawTcpReceiveResult.Failure(
            when (failure) {
                IncomingUploadFailure.INTEGRITY -> RawTcpFailure.HASH_MISMATCH
                IncomingUploadFailure.STORAGE_FULL -> RawTcpFailure.RECEIVER_STORAGE_FULL
                IncomingUploadFailure.STORAGE_UNAVAILABLE ->
                    RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE
                IncomingUploadFailure.WRITE_FAILED -> RawTcpFailure.RECEIVER_WRITE_FAILED
                IncomingUploadFailure.INVALID_REQUEST,
                null -> RawTcpFailure.IO_ERROR
            }
        )
    }

    override fun onRawFileError(offerId: String, fileIndex: Int, failure: RawTcpFailure) {
        val incomingFailure = when (failure) {
            RawTcpFailure.HASH_MISMATCH,
            RawTcpFailure.SIZE_MISMATCH -> IncomingUploadFailure.INTEGRITY
            RawTcpFailure.RECEIVER_STORAGE_FULL -> IncomingUploadFailure.STORAGE_FULL
            RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE ->
                IncomingUploadFailure.STORAGE_UNAVAILABLE
            RawTcpFailure.RECEIVER_WRITE_FAILED -> IncomingUploadFailure.WRITE_FAILED
            else -> null
        }
        transferEngine.failIncomingFile(offerId, fileIndex, incomingFailure)
        closeRawTransfer(offerId)
    }

    private fun closeRawTransfer(transferId: String) {
        rawTransferGrants.revoke(transferId)
        if (activeRawTransferId == transferId) activeRawTransferId = null
        rawTcpFileTransport.stop()
    }

    private fun closeActiveRawTransfer() {
        val transferId = activeRawTransferId ?: return
        closeRawTransfer(transferId)
    }
}

private fun RawTcpFailure.logCode(): String = when (this) {
    RawTcpFailure.LISTENER_START_FAILED -> "raw_tcp_listener_start_failed"
    RawTcpFailure.PORT_BIND_FAILED -> "raw_tcp_port_bind_failed"
    RawTcpFailure.ACCEPT_TIMEOUT -> "raw_tcp_accept_timeout"
    RawTcpFailure.CONNECTION_REFUSED -> "raw_tcp_connection_refused"
    RawTcpFailure.CONNECT_TIMEOUT -> "raw_tcp_connect_timeout"
    RawTcpFailure.READ_TIMEOUT -> "raw_tcp_read_timeout"
    RawTcpFailure.WRITE_FAILED -> "raw_tcp_write_failed"
    RawTcpFailure.TOKEN_INVALID -> "raw_tcp_token_invalid"
    RawTcpFailure.HEADER_INVALID -> "raw_tcp_header_invalid"
    RawTcpFailure.SIZE_MISMATCH -> "raw_tcp_size_mismatch"
    RawTcpFailure.HASH_MISMATCH -> "raw_tcp_hash_mismatch"
    RawTcpFailure.CANCELLED -> "raw_tcp_cancelled"
    RawTcpFailure.RECEIVER_UNAVAILABLE -> "raw_tcp_receiver_unavailable"
    else -> "raw_tcp_${name.lowercase()}"
}
