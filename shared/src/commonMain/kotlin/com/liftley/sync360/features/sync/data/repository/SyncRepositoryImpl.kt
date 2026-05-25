package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.debug.agentDebugLog
import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.data.network.FileTransferManager
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.HttpSyncServer
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

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class SyncRepositoryImpl(
    private val discoveryService: NetworkDiscoveryService,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val localLanIp: String,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = 8080,
    private val httpClient: HttpSyncClient = HttpSyncClient(syncPort),
    private val httpServer: HttpSyncServer = HttpSyncServer(syncPort),
    private val fileTransferManager: FileTransferManager = FileTransferManager(platformOperations, httpClient)
) : SyncRepository, SyncServerListener {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    override val activeDeviceId: Flow<String?> = _activeDeviceId.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: Flow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val nearbyDevices: Flow<List<DeviceProfile>> = _nearbyDevices.asStateFlow()

    private val _pendingIncoming = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val pendingIncomingConnectRequests: Flow<List<DeviceProfile>> = _pendingIncoming.asStateFlow()

    private val _pendingOutgoing = MutableStateFlow<DeviceProfile?>(null)
    override val pendingOutgoingConnectDevice: Flow<DeviceProfile?> = _pendingOutgoing.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    // --- State ---
    private val _pairedDevicesList = MutableStateFlow<List<DeviceProfile>>(emptyList())
    private val _currentMessagesList = MutableStateFlow<List<SyncMessage>>(emptyList())
    private val conversationMessagesMap = mutableMapOf<String, List<SyncMessage>>()
    private val _incomingFileOffer = MutableStateFlow<IncomingFileOffer?>(null)
    override val incomingFileOffer: Flow<IncomingFileOffer?> = _incomingFileOffer.asStateFlow()
    private val _fileTransferProgress = MutableStateFlow<FileTransferProgress?>(null)
    override val fileTransferProgress: Flow<FileTransferProgress?> = _fileTransferProgress.asStateFlow()
    private val _receivedFileBatch = MutableStateFlow<ReceivedFileBatch?>(null)
    override val receivedFileBatch: Flow<ReceivedFileBatch?> = _receivedFileBatch.asStateFlow()

    override val pairedDevices: Flow<List<DeviceProfile>> = combine(
        _pairedDevicesList,
        _nearbyDevices,
        _activeDeviceId,
        connectionStatus
    ) { pairedList, nearby, activeId, status ->
        pairedList.map { entity ->
            val isCurrentlyConnected = entity.id == activeId && status == ConnectionStatus.CONNECTED
            val live = nearby.firstOrNull { it.id == entity.id }
            entity.copy(
                hostAddress = live?.hostAddress ?: entity.hostAddress,
                isOnline = live != null || isCurrentlyConnected
            )
        }
    }

    override val conversationMessages: Flow<List<SyncMessage>> = _currentMessagesList.asStateFlow()

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
            _activeDeviceId.collect { activeId ->
                if (activeId == null) {
                    _currentMessagesList.value = emptyList()
                    platformOperations.stopService()
                } else {
                    _currentMessagesList.value = conversationMessagesMap[activeId] ?: emptyList()
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
        val activeId = _activeDeviceId.value ?: return null
        return _pairedDevicesList.value.firstOrNull { it.id == activeId }?.hostAddress
    }

    override fun requestConnect(device: DeviceProfile) {
        _pendingOutgoing.value = device
    }

    override fun confirmOutgoingConnect() {
        val device = _pendingOutgoing.value ?: return
        val host = device.hostAddress
        if (host == null) {
            _pendingOutgoing.value = null
            return
        }
        
        scope.launch {
            val req = ConnectRequestDto(localDevice.id, localDevice.name, localDevice.type.name, localLanIp)
            httpClient.sendConnectRequest(host, req)
        }
    }

    override fun dismissOutgoingConnect() {
        _pendingOutgoing.value = null
    }

    override fun acceptIncomingConnect(deviceId: String) {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return
        _pendingIncoming.value = _pendingIncoming.value.filter { it.id != deviceId }
        
        persistDevice(device)
        _activeDeviceId.value = device.id
        _connectionStatus.value = ConnectionStatus.CONNECTED

        device.hostAddress?.let { host ->
            scope.launch {
                val acc = ConnectAcceptDto(localDevice.id, localDevice.name, localDevice.type.name, localLanIp)
                httpClient.sendConnectAccept(host, acc)
            }
        }
    }

    override fun declineIncomingConnect(deviceId: String) {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return
        _pendingIncoming.value = _pendingIncoming.value.filter { it.id != deviceId }
        
        device.hostAddress?.let { host ->
            scope.launch {
                httpClient.sendConnectReject(host)
            }
        }
    }

    override fun switchActiveDevice(deviceId: String) {
        _activeDeviceId.value = deviceId
        _connectionStatus.value = ConnectionStatus.CONNECTED
    }

    override fun disconnectActivePeer() {
        val ip = getActivePeerIp()
        if (ip != null) {
            scope.launch { httpClient.sendConnectReject(ip) }
        }
        clearActiveSession()
    }

    override fun disconnectAll() {
        disconnectActivePeer()
        stopSync()
    }

    override fun sendText(text: String) {
        val ip = getActivePeerIp() ?: return
        val peerId = _activeDeviceId.value ?: return
        val msg = MessageDto(
            messageId = generateUniqueId(),
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            content = text,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        persistMessage(msg.messageId, peerId, msg.senderDeviceId, msg.content, msg.timestamp)
        
        scope.launch {
            httpClient.sendTextMessage(ip, msg)
        }
    }

    override fun offerFiles(files: List<PickedFile>) {
        val ip = getActivePeerIp() ?: return
        if (files.isEmpty()) return
        val offerId = generateUniqueId()
        
        fileTransferManager.registerOutgoingFiles(offerId, files)
        
        val offer = FileOfferDto(
            offerId = offerId,
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            files = files.map { FilePreviewDto(it.name, it.mimeType, it.sizeBytes) }
        )
        
        scope.launch {
            // #region agent log
            agentDebugLog(
                location = "SyncRepositoryImpl.kt:offerFiles",
                message = "sending file offer",
                hypothesisId = "B",
                data = mapOf(
                    "offerId" to offerId,
                    "peerIp" to ip,
                    "fileCount" to files.size.toString(),
                    "totalBytes" to files.sumOf { it.sizeBytes }.toString()
                )
            )
            // #endregion
            httpClient.sendFileOffer(ip, offer)
        }
    }

    override fun acceptFileOffer(offerId: String) {
        val ip = getActivePeerIp() ?: return
        val offer = _incomingFileOffer.value?.takeIf { it.offerId == offerId } ?: return
        _incomingFileOffer.value = null
        
        _fileTransferProgress.value = FileTransferProgress(
            peerName = offer.senderName,
            files = offer.files,
            percent = 0,
            direction = TransferDirection.RECEIVING
        )
        
        scope.launch(Dispatchers.IO) {
            // #region agent log
            agentDebugLog(
                location = "SyncRepositoryImpl.kt:acceptFileOffer",
                message = "accepting file offer, starting download",
                hypothesisId = "A",
                data = mapOf(
                    "offerId" to offerId,
                    "serverIp" to ip,
                    "serverPort" to syncPort.toString(),
                    "totalBytes" to offer.files.sumOf { it.sizeBytes }.toString()
                ),
                runId = "post-fix-3"
            )
            // #endregion
            httpClient.sendFileAccept(ip, FileAcceptDto(offerId, localDevice.id))
            
            val savedPaths = fileTransferManager.downloadFiles(
                serverIp = ip,
                serverPort = syncPort,
                offerId = offerId,
                files = offer.files.map { FilePreviewDto(it.name, it.mimeType, it.sizeBytes) }
            ) { percent ->
                updateProgress(percent)
            }
            
            if (savedPaths != null) {
                updateProgress(100)
                httpClient.sendFileComplete(ip, FileCompleteDto(offerId, localDevice.id))
                delay(900.milliseconds)
                _fileTransferProgress.value = null
                _receivedFileBatch.value = ReceivedFileBatch(
                    senderName = offer.senderName,
                    files = offer.files,
                    savedPaths = savedPaths
                )
            } else {
                // #region agent log
                agentDebugLog(
                    location = "SyncRepositoryImpl.kt:acceptFileOffer",
                    message = "download failed, clearing progress UI",
                    hypothesisId = "C",
                    data = mapOf("offerId" to offerId)
                )
                // #endregion
                _fileTransferProgress.value = null
            }
        }
    }

    override fun declineFileOffer(offerId: String) {
        val ip = getActivePeerIp() ?: return
        _incomingFileOffer.value = null
        scope.launch {
            httpClient.sendFileReject(ip, FileRejectDto(offerId, localDevice.id))
        }
    }

    override fun dismissReceivedFiles() {
        _receivedFileBatch.value = null
    }

    override suspend fun clearAllData() {
        clearActiveSession()
    }

    override fun deleteDevice(deviceId: String) {
        conversationMessagesMap.remove(deviceId)
        _pairedDevicesList.value = _pairedDevicesList.value.filter { it.id != deviceId }
        if (_activeDeviceId.value == deviceId) {
            clearActiveSession()
        }
    }

    // --- HTTP Server Listener Callbacks ---

    override fun onConnectRequest(request: ConnectRequestDto) {
        val device = DeviceProfile(
            id = request.deviceId,
            name = request.deviceName,
            type = parseDeviceType(request.deviceType),
            hostAddress = request.senderIp,
            isOnline = true
        )
        if (_pendingIncoming.value.none { it.id == device.id }) {
            _pendingIncoming.value += device
        }
    }

    override fun onConnectAccept(accept: ConnectAcceptDto) {
        _pendingOutgoing.value = null
        persistDevice(
            DeviceProfile(
                id = accept.deviceId,
                name = accept.deviceName,
                type = parseDeviceType(accept.deviceType),
                hostAddress = accept.senderIp,
                isOnline = true
            )
        )
        _activeDeviceId.value = accept.deviceId
        _connectionStatus.value = ConnectionStatus.CONNECTED
    }

    override fun onConnectReject() {
        clearActiveSession()
    }

    override fun onTextMessage(message: MessageDto) {
        val peerId = message.senderDeviceId
        persistMessage(message.messageId, peerId, peerId, message.content, message.timestamp)
        incomingNotifier.notifyIncoming(message.senderName, message.content.take(120), false)
    }

    override fun onFileOffer(offer: FileOfferDto) {
        _incomingFileOffer.value = IncomingFileOffer(
            offerId = offer.offerId,
            senderDeviceId = offer.senderDeviceId,
            senderName = offer.senderName,
            files = offer.files.map { TransferFilePreview(it.fileName, it.mimeType, it.fileSize) }
        )
        incomingNotifier.notifyIncoming(offer.senderName, "${offer.files.size} files offered", true)
    }

    override fun onFileAccept(accept: FileAcceptDto) {
        val files = fileTransferManager.getOutgoingFiles(accept.offerId)
        // #region agent log
        agentDebugLog(
            location = "SyncRepositoryImpl.kt:onFileAccept",
            message = "peer accepted file offer",
            hypothesisId = "B",
            data = mapOf(
                "offerId" to accept.offerId,
                "hasOutgoingBatch" to (files != null).toString(),
                "fileCount" to (files?.size?.toString() ?: "0"),
                "totalBytes" to (files?.sumOf { it.sizeBytes }?.toString() ?: "0")
            )
        )
        // #endregion
        if (files == null) return
        _fileTransferProgress.value = FileTransferProgress(
            peerName = "Peer",
            files = files.map { TransferFilePreview(it.name, it.mimeType, it.sizeBytes) },
            percent = 1,
            direction = TransferDirection.SENDING
        )
        // Note: receiver is now pulling chunks via GET /files/...
    }

    override fun onFileReject(reject: FileRejectDto) {
        fileTransferManager.clearOutgoingFiles(reject.offerId)
    }

    override fun onFileComplete(complete: FileCompleteDto) {
        fileTransferManager.clearOutgoingFiles(complete.offerId)
        updateProgress(100)
        scope.launch {
            delay(900.milliseconds)
            _fileTransferProgress.value = null
        }
    }

    override fun getOutgoingFileSize(offerId: String, fileIndex: Int): Long? =
        fileTransferManager.getOutgoingFileSize(offerId, fileIndex)

    override suspend fun serveFileChunk(offerId: String, fileIndex: Int, chunkSizeBytes: Int, onChunk: suspend (ByteArray) -> Unit) {
        // #region agent log
        agentDebugLog(
            location = "SyncRepositoryImpl.kt:serveFileChunk",
            message = "serveFileChunk invoked",
            hypothesisId = "E",
            data = mapOf(
                "offerId" to offerId,
                "fileIndex" to fileIndex.toString(),
                "chunkSizeBytes" to chunkSizeBytes.toString()
            )
        )
        // #endregion
        fileTransferManager.serveFileChunk(offerId, fileIndex, chunkSizeBytes, ::updateProgress) {
            onChunk(it)
        }
    }

    // --- Helpers ---

    private fun clearActiveSession() {
        _activeDeviceId.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _currentMessagesList.value = emptyList()
        _incomingFileOffer.value = null
        _fileTransferProgress.value = null
        _receivedFileBatch.value = null
    }

    private fun persistDevice(device: DeviceProfile) {
        val current = _pairedDevicesList.value
        if (current.none { it.id == device.id }) {
            _pairedDevicesList.value = current + device
        } else {
            _pairedDevicesList.value = current.map { 
                if (it.id == device.id) device.copy(hostAddress = device.hostAddress ?: it.hostAddress) else it 
            }
        }
    }

    private fun persistMessage(itemId: String, peerId: String, originId: String, content: String, timestamp: Long) {
        val msg = SyncMessage(
            id = itemId,
            peerDeviceId = peerId,
            text = content,
            isFromMe = originId == localDevice.id,
            timestamp = timestamp,
            isFile = false,
            fileName = null
        )
        val list = conversationMessagesMap[peerId] ?: emptyList()
        val updated = list + msg
        conversationMessagesMap[peerId] = updated
        if (_activeDeviceId.value == peerId) {
            _currentMessagesList.value = updated
        }
    }

    private fun updateProgress(percent: Int) {
        val current = _fileTransferProgress.value
        if (current != null && current.percent != percent) {
            _fileTransferProgress.update { it?.copy(percent = percent) }
        }
    }

    private fun generateUniqueId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${(1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
    }

    private fun parseDeviceType(value: String): DeviceType =
        runCatching { DeviceType.valueOf(value) }.getOrDefault(DeviceType.DESKTOP)
}
