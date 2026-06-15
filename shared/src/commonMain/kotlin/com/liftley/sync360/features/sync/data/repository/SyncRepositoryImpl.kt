package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.features.sync.data.network.FileTransferManager
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.HttpSyncServer
import com.liftley.sync360.features.sync.data.network.OutgoingFileTransferCoordinator
import com.liftley.sync360.features.sync.data.network.SyncServerListener
import com.liftley.sync360.features.sync.data.network.api.*
import com.liftley.sync360.features.sync.domain.model.*
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SyncRepositoryImpl(
    private val discoveryService: NetworkDiscoveryService,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val localLanIp: String,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = 8080,
    private val httpClient: HttpSyncClient = HttpSyncClient(syncPort),
    private val httpServer: HttpSyncServer = HttpSyncServer(syncPort),
    private val fileTransferManager: FileTransferManager = FileTransferManager(platformOperations, httpClient),
    private val outgoingFileTransferCoordinator: OutgoingFileTransferCoordinator =
        OutgoingFileTransferCoordinator(localDevice, httpClient, fileTransferManager)
) : SyncRepository, SyncServerListener {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val deviceSession = DeviceSessionStore()
    override val activeDeviceId: Flow<String?> = deviceSession.activeDeviceId
    override val connectionStatus: Flow<ConnectionStatus> = deviceSession.connectionStatus

    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val nearbyDevices: Flow<List<DeviceProfile>> = _nearbyDevices.asStateFlow()

    override val pendingIncomingConnectRequests: Flow<List<DeviceProfile>> = deviceSession.pendingIncoming
    override val pendingOutgoingConnectDevice: Flow<DeviceProfile?> = deviceSession.pendingOutgoing

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    // --- State ---
    private val deviceRegistry = DeviceRegistry()
    private val pendingSessionTokens = mutableMapOf<String, String>()
    private val sessionAuthenticator = SessionAuthenticator(localDevice, localLanIp)
    private val incomingFileTransferCoordinator =
        IncomingFileTransferCoordinator(fileTransferManager, sessionAuthenticator)
    private val _currentMessagesList = MutableStateFlow<List<SyncMessage>>(emptyList())
    private val sessionTextStore = SessionTextStore()
    private val _fileTransferProgress = MutableStateFlow<FileTransferProgress?>(null)
    override val fileTransferProgress: Flow<FileTransferProgress?> = _fileTransferProgress.asStateFlow()
    private val _fileTransferFailure = MutableStateFlow<FileTransferFailure?>(null)
    override val fileTransferFailure: Flow<FileTransferFailure?> = _fileTransferFailure.asStateFlow()
    private val _receivedFileBatch = MutableStateFlow<ReceivedFileBatch?>(null)
    override val receivedFileBatch: Flow<ReceivedFileBatch?> = _receivedFileBatch.asStateFlow()

    override val sessionDevices: Flow<List<DeviceProfile>> = combine(
        deviceRegistry.approvedDevices,
        _nearbyDevices,
        deviceSession.activeDeviceId,
        connectionStatus
    ) { sessionList, nearby, activeId, status ->
        sessionList.map { entity ->
            val isCurrentlyConnected = entity.id == activeId && status == ConnectionStatus.CONNECTED
            val live = nearby.firstOrNull { it.id == entity.id }
            entity.copy(
                hostAddress = live?.hostAddress ?: entity.hostAddress,
                isOnline = live != null || isCurrentlyConnected
            )
        }
    }

    override val sessionMessages: Flow<List<SyncMessage>> = _currentMessagesList.asStateFlow()

    init {
        httpServer.listener = this

        scope.launch {
            discoveryService.discoveredDevices.collect { discovered ->
                _nearbyDevices.value = discovered.filter { 
                    it.id != localDevice.id && 
                    it.hostAddress != localLanIp && 
                    it.hostAddress != "127.0.0.1" && 
                    it.hostAddress != "localhost"
                }
            }
        }

        scope.launch {
            deviceSession.activeDeviceId.collect { activeId ->
                if (activeId == null) {
                    _currentMessagesList.value = emptyList()
                    platformOperations.stopService()
                } else {
                    _currentMessagesList.value = sessionTextStore.messagesFor(activeId)
                    platformOperations.startService("")
                }
            }
        }
    }

    override fun startSync() {
        scope.launch(Dispatchers.IO) {
            discoveryService.registerHost(
                port = syncPort,
                deviceId = localDevice.id,
                deviceName = localDevice.name,
                deviceType = localDevice.type.name
            )
        }
        httpServer.start()
        triggerManualScan()
    }

    override fun stopSync() {
        scanJob?.cancel()
        _isScanning.value = false
        scope.launch(Dispatchers.IO) {
            discoveryService.stopDiscovery()
        }
        httpServer.stop()
    }

    override fun triggerManualScan() {
        scanJob?.cancel()
        _isScanning.value = true
        scope.launch(Dispatchers.IO) {
            discoveryService.startDiscovery()
        }
        scanJob = scope.launch {
            delay(10000.milliseconds)
            withContext(Dispatchers.IO) {
                discoveryService.stopDiscovery()
            }
            _isScanning.value = false
        }
    }

    private fun getActivePeerIp(): String? {
        val activeId = deviceSession.activeDeviceId.value ?: return null
        return deviceRegistry.hostFor(activeId)
    }

    override fun requestConnect(device: DeviceProfile) {
        deviceSession.requestOutgoing(device)
    }

    override fun requestConnectByHost(hostAddress: String) {
        val host = hostAddress.trim()
        if (host.isBlank()) return
        deviceSession.requestOutgoing(
            DeviceProfile(
                id = "manual:$host",
                name = host,
                type = DeviceType.DESKTOP,
                hostAddress = host,
                isOnline = true
            )
        )
    }

    override fun confirmOutgoingConnect() {
        val device = deviceSession.pendingOutgoing.value ?: return
        val host = device.hostAddress
        if (host == null) {
            deviceSession.clearOutgoing()
            return
        }
        
        scope.launch {
            val token = sessionAuthenticator.newSessionToken()
            pendingSessionTokens[device.id] = token
            httpClient.sendConnectRequest(host, sessionAuthenticator.connectRequest(token))
        }
    }

    override fun dismissOutgoingConnect() {
        deviceSession.clearOutgoing()
    }

    override fun acceptIncomingConnect(deviceId: String) {
        val device = deviceSession.removeIncoming(deviceId) ?: return
        val sessionToken = pendingSessionTokens.remove(device.id) ?: sessionAuthenticator.newSessionToken()
        
        approveSessionDevice(device, sessionToken)
        deviceSession.connect(device.id)

        device.hostAddress?.let { host ->
            scope.launch {
                httpClient.sendConnectAccept(host, sessionAuthenticator.connectAccept(sessionToken))
            }
        }
    }

    override fun declineIncomingConnect(deviceId: String) {
        val device = deviceSession.removeIncoming(deviceId) ?: return
        val sessionToken = pendingSessionTokens.remove(device.id)
        
        device.hostAddress?.let { host ->
            scope.launch {
                httpClient.sendConnectReject(host, sessionAuthenticator.connectReject(sessionToken))
            }
        }
    }

    override fun switchActiveDevice(deviceId: String) {
        deviceSession.connect(deviceId)
    }

    override fun disconnectActivePeer() {
        if (hasActiveTransfer()) return

        val ip = getActivePeerIp()
        if (ip != null) {
            val sessionToken = deviceSession.activeDeviceId.value?.let(deviceRegistry::sessionTokenFor)
            scope.launch { httpClient.sendConnectReject(ip, sessionAuthenticator.connectReject(sessionToken)) }
        }
        clearActiveSession()
    }

    override fun disconnectAll() {
        if (hasActiveTransfer()) return

        disconnectActivePeer()
        stopSync()
    }

    override fun sendText(text: String) {
        val ip = getActivePeerIp() ?: return
        val peerId = deviceSession.activeDeviceId.value ?: return
        val sessionToken = deviceRegistry.sessionTokenFor(peerId) ?: return
        val msg = MessageDto(
            messageId = generateUniqueId(),
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            content = text,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            sessionToken = sessionToken,
            issuedAtMillis = 0L,
            nonce = "",
            signature = ""
        )
        val signedMessage = sessionAuthenticator.signTextMessage(msg)
        appendSessionMessage(signedMessage.messageId, peerId, signedMessage.senderDeviceId, signedMessage.content, signedMessage.timestamp)
        
        scope.launch {
            httpClient.sendTextMessage(ip, signedMessage)
        }
    }

    override fun offerFiles(files: List<PickedFile>) {
        if (hasActiveTransfer()) {
            setTransferFailure("A transfer is already in progress", TransferDirection.SENDING)
            return
        }

        val ip = getActivePeerIp() ?: return
        val peerId = deviceSession.activeDeviceId.value ?: return
        val sessionToken = deviceRegistry.sessionTokenFor(peerId) ?: return
        if (files.isEmpty()) return
        val offerId = generateUniqueId()
        
        scope.launch {
            val previews = outgoingFileTransferCoordinator.previews(files)
            onTransferStarted()
            _fileTransferFailure.value = null
            _fileTransferProgress.value = FileTransferProgress(
                peerName = "Peer",
                files = previews,
                percent = 1,
                direction = TransferDirection.SENDING
            )
            
            val success = outgoingFileTransferCoordinator.sendFiles(
                peerHost = ip,
                offerId = offerId,
                files = files,
                sessionToken = sessionToken,
                onProgress = ::updateProgress
            )
            
            if (success) {
                updateProgress(100)
                delay(900.milliseconds)
                _fileTransferProgress.value = null
                onTransferFinished()
            } else {
                setTransferFailure("File transfer failed", TransferDirection.SENDING)
                _fileTransferProgress.value = null
                onTransferFinished()
            }
        }
    }

    override fun dismissReceivedFiles() {
        _receivedFileBatch.value = null
    }

    override fun dismissTransferFailure() {
        _fileTransferFailure.value = null
    }

    override suspend fun clearAllData() {
        clearActiveSession()
    }

    override fun deleteDevice(deviceId: String) {
        if (deviceSession.activeDeviceId.value == deviceId && hasActiveTransfer()) return

        sessionTextStore.removePeer(deviceId)
        deviceRegistry.delete(deviceId)
        if (deviceSession.activeDeviceId.value == deviceId) {
            clearActiveSession()
        }
    }

    // --- HTTP Server Listener Callbacks ---

    override fun onConnectRequest(request: ConnectRequestDto) {
        if (!sessionAuthenticator.verifyConnectRequest(request)) return

        val device = DeviceProfile(
            id = request.deviceId,
            name = request.deviceName,
            type = parseDeviceType(request.deviceType),
            hostAddress = request.senderIp,
            isOnline = true
        )
        if (isApprovedSessionPeer(device.id, request.sessionToken)) {
            approveSessionDevice(device, request.sessionToken)
            deviceSession.connect(device.id)
            device.hostAddress?.let { host ->
                scope.launch {
                    httpClient.sendConnectAccept(host, sessionAuthenticator.connectAccept(request.sessionToken))
                }
            }
            return
        }

        pendingSessionTokens[device.id] = request.sessionToken
        deviceSession.addIncoming(device)
    }

    override fun onConnectAccept(accept: ConnectAcceptDto) {
        if (!sessionAuthenticator.verifyConnectAccept(accept)) return

        val pending = deviceSession.pendingOutgoing.value
        val pendingToken = pendingSessionTokens[accept.deviceId] ?: pending?.id?.let { pendingSessionTokens[it] }
        val alreadyApproved = isApprovedSessionPeer(accept.deviceId, accept.sessionToken)
        val matchesPendingDevice = pending?.id == accept.deviceId || pending?.hostAddress == accept.senderIp
        val acceptsPendingRequest = matchesPendingDevice && pendingToken == accept.sessionToken
        if (!acceptsPendingRequest && !alreadyApproved) return

        deviceSession.clearOutgoing()
        pendingSessionTokens.remove(accept.deviceId)
        pending?.id?.let { pendingSessionTokens.remove(it) }
        approveSessionDevice(
            DeviceProfile(
                id = accept.deviceId,
                name = accept.deviceName,
                type = parseDeviceType(accept.deviceType),
                hostAddress = accept.senderIp,
                isOnline = true
            ),
            accept.sessionToken
        )
        deviceSession.connect(accept.deviceId)
    }

    override fun onConnectReject(reject: ConnectRejectDto): Boolean {
        if (!sessionAuthenticator.verifyConnectReject(reject)) return false

        val pendingOutgoingId = deviceSession.pendingOutgoing.value?.id
        val activeDeviceId = deviceSession.activeDeviceId.value
        val tokenMatchesPending = reject.sessionToken != null && pendingSessionTokens.values.any { it == reject.sessionToken }
        val allowed = hasApprovedSession(reject.senderDeviceId, reject.sessionToken) ||
            (pendingOutgoingId == reject.senderDeviceId && pendingSessionTokens[reject.senderDeviceId] == reject.sessionToken) ||
            tokenMatchesPending
        if (!allowed) return false

        if (pendingOutgoingId == reject.senderDeviceId || tokenMatchesPending) {
            deviceSession.clearOutgoing()
            val rejectedToken = reject.sessionToken
            pendingSessionTokens
                .filterValues { it == rejectedToken }
                .keys
                .toList()
                .forEach { pendingSessionTokens.remove(it) }
        }
        if (activeDeviceId == reject.senderDeviceId) {
            if (hasActiveTransfer()) return false
            clearActiveSession()
        }
        return true
    }

    override fun onTextMessage(message: MessageDto): Boolean {
        if (!isApprovedSessionPeer(message.senderDeviceId, message.sessionToken)) return false
        if (!sessionAuthenticator.verifyTextMessage(message)) return false

        val peerId = message.senderDeviceId
        appendSessionMessage(message.messageId, peerId, peerId, message.content, message.timestamp)
        incomingNotifier.notifyIncoming(message.senderName, message.content.take(120), false)
        return true
    }

    override fun onFileOffer(offer: FileOfferDto): Boolean {
        val start = incomingFileTransferCoordinator.startOffer(
            offer = offer,
            isApprovedSession = isApprovedSessionPeer(offer.senderDeviceId, offer.sessionToken),
            hasActiveTransfer = hasActiveTransfer(),
            onProgress = ::updateProgress
        ) ?: return false

        onTransferStarted()
        _fileTransferFailure.value = null
        _fileTransferProgress.value = start.progress
        _receivedFileBatch.value = null
        incomingNotifier.notifyIncoming(start.senderName, "Receiving ${start.fileCount} files...", true)
        return true
    }

    override fun onFileComplete(complete: FileCompleteDto): Boolean {
        val accepted = incomingFileTransferCoordinator.completeSignal(
            complete = complete,
            isApprovedSession = isApprovedSessionPeer(complete.senderDeviceId, complete.sessionToken)
        )
        if (!accepted) return false

        updateProgress(100)
        scope.launch {
            delay(900.milliseconds)
            _fileTransferProgress.value = null
            onTransferFinished()
        }
        return true
    }

    override fun onIncomingFileChunkInit(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields
    ): Boolean {
        return incomingFileTransferCoordinator.initFileWrite(offerId, fileIndex, sessionToken, authFields)
    }

    override fun onIncomingFileChunkReceived(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean {
        return incomingFileTransferCoordinator.writeChunk(offerId, fileIndex, chunk)
    }

    override fun onIncomingFileChunkComplete(offerId: String, fileIndex: Int): String? {
        val complete = incomingFileTransferCoordinator.completeFileWrite(offerId, fileIndex)
        if (complete.batch != null) {
            _receivedFileBatch.value = complete.batch
            _fileTransferProgress.value = null
            onTransferFinished()
        }
        return complete.savedPath
    }

    override fun onIncomingFileChunkError(offerId: String, fileIndex: Int) {
        incomingFileTransferCoordinator.errorFileWrite(offerId, fileIndex)
        setTransferFailure("Receiving file failed", TransferDirection.RECEIVING)
        _fileTransferProgress.value = null
        onTransferFinished()
    }

    // --- Helpers ---

    private fun clearActiveSession() {
        deviceSession.disconnect()
        pendingSessionTokens.clear()
        sessionAuthenticator.clearReplayHistory()
        _currentMessagesList.value = emptyList()
        _fileTransferProgress.value = null
        _fileTransferFailure.value = null
        _receivedFileBatch.value = null
        incomingFileTransferCoordinator.clear()
    }

    private fun approveSessionDevice(device: DeviceProfile, sessionToken: String) {
        deviceRegistry.upsert(device, sessionToken)
    }

    private fun isApprovedSessionPeer(deviceId: String, sessionToken: String): Boolean {
        return deviceRegistry.hasValidSession(deviceId, sessionToken)
    }

    private fun hasApprovedSession(deviceId: String, sessionToken: String?): Boolean {
        return sessionToken != null && deviceRegistry.hasValidSession(deviceId, sessionToken)
    }

    private fun appendSessionMessage(itemId: String, peerId: String, originId: String, content: String, timestamp: Long) {
        val updated = sessionTextStore.addText(
            itemId = itemId,
            peerId = peerId,
            originDeviceId = originId,
            localDeviceId = localDevice.id,
            text = content,
            timestamp = timestamp
        )
        if (deviceSession.activeDeviceId.value == peerId) {
            _currentMessagesList.value = updated
        }
    }

    private fun updateProgress(percent: Int) {
        val current = _fileTransferProgress.value
        if (current != null && current.percent != percent) {
            _fileTransferProgress.update { it?.copy(percent = percent) }
        }
    }

    private fun hasActiveTransfer(): Boolean {
        return _fileTransferProgress.value != null
    }

    private fun setTransferFailure(
        message: String,
        direction: TransferDirection,
        failedFileName: String? = null
    ) {
        val peerName = _fileTransferProgress.value?.peerName ?: deviceSession.activeDeviceId.value ?: "Peer"
        _fileTransferFailure.value = FileTransferFailure(
            peerName = peerName,
            message = message,
            failedFileName = failedFileName,
            direction = direction
        )
    }

    private fun onTransferStarted() {
        platformOperations.startService("transfer")
    }

    private fun onTransferFinished() {
        if (deviceSession.activeDeviceId.value == null) {
            platformOperations.stopService()
        }
    }

    private fun generateUniqueId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${(1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
    }

    private fun parseDeviceType(value: String): DeviceType =
        runCatching { DeviceType.valueOf(value) }.getOrDefault(DeviceType.DESKTOP)
}
