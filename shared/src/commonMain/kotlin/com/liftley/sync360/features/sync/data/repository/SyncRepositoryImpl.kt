package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.SessionCrypto
import com.liftley.sync360.features.sync.data.network.TransferPayloadStore
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.HttpSyncServer
import com.liftley.sync360.features.sync.data.network.IncomingUploadFailure
import com.liftley.sync360.features.sync.data.network.OutgoingTransferSender
import com.liftley.sync360.features.sync.data.network.RawTcpFailure
import com.liftley.sync360.features.sync.data.network.RawTcpFileHeader
import com.liftley.sync360.features.sync.data.network.RawTcpFileListener
import com.liftley.sync360.features.sync.data.network.RawTcpFileTransport
import com.liftley.sync360.features.sync.data.network.RawTcpListenerStartResult
import com.liftley.sync360.features.sync.data.network.RawTcpReceiveResult
import com.liftley.sync360.features.sync.data.network.RawTransferGrantStore
import com.liftley.sync360.features.sync.data.network.SyncServerListener
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferResponseDto
import com.liftley.sync360.features.sync.domain.network.DiscoveryScanState
import com.liftley.sync360.features.sync.domain.network.LocalPeerDiscovery
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.SyncStartResult
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class SyncRepositoryImpl(
    private val peerDiscovery: LocalPeerDiscovery,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = 8080,
    private val httpClient: HttpSyncClient = HttpSyncClient(syncPort),
    private val httpServer: HttpSyncServer = HttpSyncServer(syncPort),
    private val rawTcpFileTransport: RawTcpFileTransport = RawTcpFileTransport(),
    transferPayloadStore: TransferPayloadStore =
        TransferPayloadStore(platformOperations),
    outgoingTransferSender: OutgoingTransferSender =
        OutgoingTransferSender(
            localDevice,
            httpClient,
            transferPayloadStore,
            rawTcpFileTransport
        )
) : SyncRepository, SyncServerListener, RawTcpFileListener {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runtimeLock = Any()
    private var syncStarted = false
    private var runtimeClosed = false
    private var activeRawTransferId: String? = null
    private val incomingTransferLock = Any()
    private val rawTransferGrants = RawTransferGrantStore()

    private val incomingOfferDecisions = IncomingOfferDecisionStore()

    private val incomingTransferReceiver = IncomingTransferReceiver(
        transferPayloadStore,
        platformOperations
    )

    private val transferEngine = TransferEngine(
        scope = scope,
        incomingNotifier = incomingNotifier,
        platformOperations = platformOperations,
        incoming = incomingTransferReceiver,
        outgoing = outgoingTransferSender,
        onIncomingTerminated = ::closeActiveRawTransfer
    )

    init {
        httpServer.listener = this
        rawTcpFileTransport.listener = this
        scope.launch {
            peerDiscovery.peers.collect(::publishNearbyDevices)
        }
    }

    private val _clipboardHistory = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    override val clipboardHistory: Flow<List<ClipboardEntry>> = _clipboardHistory.asStateFlow()

    private fun addToClipboardHistory(text: String, senderName: String) {
        val label = "Received from $senderName"
        val entry = ClipboardEntry(text = text, senderName = label)
        _clipboardHistory.update { current ->
            listOf(entry) + current
        }
    }

    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val nearbyDevices: Flow<List<DeviceProfile>> = _nearbyDevices.asStateFlow()
    override val isScanning: Flow<Boolean> = peerDiscovery.state.map { state ->
        state.scan == DiscoveryScanState.STARTING || state.scan == DiscoveryScanState.ACTIVE
    }
    override val quickSaveEnabled = incomingOfferDecisions.quickSaveEnabled
    override val pendingIncomingOffer = incomingOfferDecisions.pendingOffer
    override val transferSnapshot: Flow<TransferSnapshot> = transferEngine.snapshot

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

        if (!httpServer.start()) {
            synchronized(runtimeLock) { syncStarted = false }
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

    override fun setQuickSaveEnabled(enabled: Boolean) =
        incomingOfferDecisions.setQuickSaveEnabled(enabled)

    override fun acceptIncomingOffer(offerId: String) {
        incomingOfferDecisions.accept(offerId)
    }

    override fun declineIncomingOffer(offerId: String) {
        incomingOfferDecisions.decline(offerId)
    }

    override suspend fun clearAllData() {
        transferEngine.cancel()
    }

    override fun offerItemsTo(deviceId: String, items: List<SendItem>) {
        val device = _nearbyDevices.value.firstOrNull { it.id == deviceId }
        if (device != null) {
            transferEngine.offerItemsTo(device, items)
        }
    }

    override fun offerItemsToHost(hostAddress: String, items: List<SendItem>) {
        val cleanHost = hostAddress.substringBefore(":")
        val portPart = hostAddress.substringAfter(":", "").toIntOrNull() ?: syncPort
        val device = DeviceProfile(
            id = "manual:$cleanHost",
            name = "Manual Target ($cleanHost)",
            type = com.liftley.sync360.features.sync.domain.model.DeviceType.PHONE,
            hostAddress = cleanHost,
            port = portPart
        )
        transferEngine.offerItemsTo(device, items)
    }

    override fun dismissReceivedFiles() = transferEngine.dismissReceivedFiles()

    override fun dismissTransferFailure() = transferEngine.dismissFailure()

    override fun cancelTransfer() {
        transferEngine.cancel()
        activeRawTransferId?.let { closeRawTransfer(it) }
    }

    override suspend fun onFileOffer(
        offer: FileOfferDto,
        remoteHost: String
    ): FileOfferResponseDto {
        val isKnownPeer = isKnownDiscoveredPeer(
            senderDeviceId = offer.senderDeviceId,
            remoteHost = remoteHost
        )
        if (quickSaveEnabled.value && !isKnownPeer) {
            return FileOfferResponseDto(
                accepted = false,
                failureReason = "sender_not_discovered"
            )
        }

        val prepared = transferEngine.prepareIncomingOffer(
            offer = offer,
            remoteHost = remoteHost,
            hasPeerGrant = isKnownPeer || !quickSaveEnabled.value
        ) ?: run {
            return FileOfferResponseDto(
                accepted = false,
                failureReason = "raw_tcp_receiver_unavailable"
            )
        }
        if (!quickSaveEnabled.value) {
            when (incomingOfferDecisions.awaitDecision(transferEngine.pendingIncomingOffer(prepared))) {
                IncomingOfferDecision.Accept -> Unit
                IncomingOfferDecision.Decline -> return FileOfferResponseDto(
                    accepted = false,
                    failureReason = "receiver_declined"
                )
                IncomingOfferDecision.TimedOut -> {
                    return FileOfferResponseDto(
                        accepted = false,
                        failureReason = "receiver_timeout"
                    )
                }
                IncomingOfferDecision.Busy -> return FileOfferResponseDto(
                    accepted = false,
                    failureReason = "receiver_busy"
                )
            }
        }
        return acceptPreparedOffer(offer, prepared, remoteHost)
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
        closeRawTransfer(header.transferId)
        return RawTcpReceiveResult.Failure(
            when (incomingFailure) {
                IncomingUploadFailure.STORAGE_FULL -> RawTcpFailure.RECEIVER_STORAGE_FULL
                IncomingUploadFailure.STORAGE_UNAVAILABLE ->
                    RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE
                IncomingUploadFailure.WRITE_FAILED -> RawTcpFailure.RECEIVER_WRITE_FAILED
                IncomingUploadFailure.INTEGRITY -> RawTcpFailure.HASH_MISMATCH
                IncomingUploadFailure.INTERRUPTED -> RawTcpFailure.CANCELLED
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
            val batch = transferEngine.receivedBatchValue
            if (batch != null) {
                processReceivedBatch(batch)
            }
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
                IncomingUploadFailure.INTERRUPTED -> RawTcpFailure.CANCELLED
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
            RawTcpFailure.PARTIAL_TRANSFER,
            RawTcpFailure.CANCELLED,
            RawTcpFailure.IO_ERROR,
            RawTcpFailure.READ_TIMEOUT,
            RawTcpFailure.TIMEOUT -> IncomingUploadFailure.INTERRUPTED
            else -> null
        }
        transferEngine.failIncomingFile(offerId, fileIndex, incomingFailure)
        closeRawTransfer(offerId)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun processReceivedBatch(batch: ReceivedFileBatch) {
        val files = mutableListOf<TransferFilePreview>()
        val savedPaths = mutableListOf<String>()
        var textCount = 0

        batch.files.forEachIndexed { index, preview ->
            val path = batch.savedPaths.getOrNull(index) ?: return@forEachIndexed
            if (path.startsWith("text_content:")) {
                try {
                    val base64 = path.substringAfter("text_content:")
                    val text = kotlin.io.encoding.Base64.decode(base64).decodeToString()
                    addToClipboardHistory(text, batch.senderName)
                    platformOperations.writeClipboard(text)
                    textCount++
                } catch (e: Exception) {
                    println("Failed to decode text content: ${e.message}")
                }
            } else {
                files.add(preview)
                savedPaths.add(path)
            }
        }

        if (textCount > 0) {
            if (files.isEmpty()) {
                transferEngine.dismissReceivedFiles()
                incomingNotifier.notifyIncoming(
                    batch.senderName,
                    "Copied $textCount text snippet${if (textCount > 1) "s" else ""} to clipboard",
                    false
                )
            } else {
                val filteredBatch = ReceivedFileBatch(
                    senderName = batch.senderName,
                    files = files,
                    savedPaths = savedPaths,
                    senderDeviceId = batch.senderDeviceId
                )
                transferEngine.updateReceivedBatch(filteredBatch)
            }
        }
    }

    private fun publishNearbyDevices(discovered: List<DeviceProfile>) {
        val localAddresses = platformOperations.getNetworkEnvironment().addressSet
        _nearbyDevices.value = discovered.filter { device ->
            device.id != localDevice.id &&
                device.hostAddress !in localAddresses &&
                device.hostAddress != "127.0.0.1" &&
                device.hostAddress != "localhost"
        }
    }
    private fun closeRawTransfer(transferId: String) {
        synchronized(incomingTransferLock) {
            rawTransferGrants.revoke(transferId)
            if (activeRawTransferId == transferId) activeRawTransferId = null
            rawTcpFileTransport.stop()
        }
    }

    private fun closeActiveRawTransfer() {
        val transferId = activeRawTransferId ?: return
        closeRawTransfer(transferId)
    }

    private fun acceptPreparedOffer(
        offer: FileOfferDto,
        prepared: PreparedIncomingFileOffer,
        remoteHost: String
    ): FileOfferResponseDto = synchronized(incomingTransferLock) {
        if (activeRawTransferId != null || transferEngine.blocksIncomingOffers) {
            return@synchronized FileOfferResponseDto(
                accepted = false,
                failureReason = "receiver_busy"
            )
        }
        transferEngine.startPreparedIncomingOffer(prepared)
        rawTcpFileTransport.listener = this
        val listener = rawTcpFileTransport.startDynamic()
        if (listener is RawTcpListenerStartResult.Failure) {
            transferEngine.cancel()
            return@synchronized FileOfferResponseDto(
                accepted = false,
                failureReason = listener.reason.logCode()
            )
        }
        val port = (listener as RawTcpListenerStartResult.Success).port
        val transferToken = SessionCrypto.secureToken()
        rawTransferGrants.register(offer.offerId, transferToken, offer.files.size)
        activeRawTransferId = offer.offerId
        FileOfferResponseDto(
            accepted = true,
            rawTcpHost = platformOperations.getNetworkEnvironment().addressForPeer(remoteHost),
            rawTcpPort = port,
            transferId = offer.offerId,
            transferToken = transferToken
        )
    }

    private fun isKnownDiscoveredPeer(senderDeviceId: String, remoteHost: String): Boolean {
        val peer = _nearbyDevices.value.firstOrNull { it.id == senderDeviceId }
            ?: return false
        val peerHost = peer.hostAddress?.normalizeHost() ?: return false
        return peerHost == remoteHost.normalizeHost()
    }
}

private fun String.normalizeHost(): String =
    trim()
        .removePrefix("/")
        .removePrefix("[")
        .removeSuffix("]")
        .substringBefore("%")
        .lowercase()

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
