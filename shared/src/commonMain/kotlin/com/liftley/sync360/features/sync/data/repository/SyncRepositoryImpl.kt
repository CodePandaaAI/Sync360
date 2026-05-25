package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.network.*
import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.*
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.network.SyncBinaryChunk
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class SyncRepositoryImpl(
    private val networkService: SyncNetworkService,
    private val discoveryService: NetworkDiscoveryService,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val localLanIp: String,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = DEFAULT_PORT
) : SyncRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    override val activeDeviceId: Flow<String?> = _activeDeviceId.asStateFlow()

    override val connectionStatus: Flow<ConnectionStatus> = networkService.connectionStatus

    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val nearbyDevices: Flow<List<DeviceProfile>> = _nearbyDevices.asStateFlow()

    private val _pendingIncoming = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val pendingIncomingConnectRequests: Flow<List<DeviceProfile>> = _pendingIncoming.asStateFlow()

    private val _pendingOutgoing = MutableStateFlow<DeviceProfile?>(null)
    override val pendingOutgoingConnectDevice: Flow<DeviceProfile?> = _pendingOutgoing.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private val pendingOutgoingFileBatches = mutableMapOf<String, List<PickedFile>>()
    private val incomingAssemblies = mutableMapOf<String, IncomingFileAssembly>()

    private val deviceNameById = mutableMapOf<String, String>()

    // --- Ephemeral Volatile RAM Storage (Active Connected Session Only) ---
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
        scope.launch {
            discoveryService.discoveredDevices.collect { discovered ->
                // Filter out self-devices using multiple robust strategies
                _nearbyDevices.value = discovered.filter { 
                    it.id != localDevice.id && 
                    it.hostAddress != localLanIp && 
                    it.hostAddress != "127.0.0.1" && 
                    it.hostAddress != "localhost"
                }
                discovered.forEach { deviceNameById[it.id] = it.name }
            }
        }

        scope.launch {
            networkService.incomingPayloads.collect { json ->
                handleIncomingPayload(json)
            }
        }
        scope.launch {
            networkService.incomingBinaryChunks.collect { chunk ->
                receiveBinaryFileChunk(chunk)
            }
        }

        scope.launch {
            var wasConnected = false
            networkService.connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    wasConnected = true
                } else if (status == ConnectionStatus.DISCONNECTED && wasConnected) {
                    wasConnected = false
                    println("Sudden network connection drop detected. Clearing active device.")
                    clearConnectedSession()
                }
            }
        }

        // Keep current messages perfectly in sync with activeDeviceId changes
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
        discoveryService.registerHost(
            port = syncPort,
            deviceId = localDevice.id,
            deviceName = localDevice.name,
            deviceType = localDevice.type.name
        )
        networkService.startServer(syncPort)
        triggerManualScan()
    }

    override fun stopSync() {
        scanJob?.cancel()
        discoveryService.stopDiscovery()
        networkService.stopServer()
        _isScanning.value = false
    }

    override fun triggerManualScan() {
        scanJob?.cancel()
        _isScanning.value = true
        discoveryService.startDiscovery()
        scanJob = scope.launch {
            delay(10000.milliseconds)
            discoveryService.stopDiscovery()
            _isScanning.value = false
            println("Autodiscovery: Scan automatically stopped after 10 seconds to save battery.")
        }
    }

    override fun requestConnect(device: DeviceProfile) {
        _pendingOutgoing.value = device
    }

    override fun confirmOutgoingConnect() {
        val device = _pendingOutgoing.value ?: return
        _pendingOutgoing.value = null
        
        // We DO NOT set activeDeviceId or pairedDevicesList here.
        // The screen transition will strictly wait until the other device accepts the request.
        val host = device.connectionHost
        if (host == null) {
            println("Client: Cannot connect - device has no valid host address")
            _pendingOutgoing.value = null
            return
        }
        networkService.connectToPeer(host, syncPort, localDevice.id)
        
        scope.launch {
            try {
                // Wait for the client socket to connect successfully (up to 8 seconds)
                withTimeout(8000.milliseconds) {
                    networkService.isClientConnected
                        .first { it }
                }
                
                // Now send the handshake request!
                sendWirePayload(
                    kind = KIND_CONNECT_REQUEST,
                    content = localLanIp,
                    targetDeviceId = device.id,
                    peerDeviceId = device.id
                )
            } catch (e: Exception) {
                println("Client: Connect handshake failed - ${e.message}")
            }
        }
    }

    override fun dismissOutgoingConnect() {
        _pendingOutgoing.value = null
    }

    override fun acceptIncomingConnect(deviceId: String) {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return
        _pendingIncoming.value = _pendingIncoming.value.filter { it.id != deviceId }
        _activeDeviceId.value = device.id
        persistDevice(device)
        sendWirePayload(
            kind = KIND_CONNECT_ACCEPT,
            content = localDevice.name,
            targetDeviceId = device.id,
            peerDeviceId = device.id
        )
    }

    override fun declineIncomingConnect(deviceId: String) {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return
        _pendingIncoming.value = _pendingIncoming.value.filter { it.id != deviceId }
        sendWirePayload(
            kind = KIND_CONNECT_REJECT,
            content = "",
            targetDeviceId = device.id,
            peerDeviceId = device.id
        )
    }

    override fun switchActiveDevice(deviceId: String) {
        _activeDeviceId.value = deviceId
    }

    override fun disconnectActivePeer() {
        val peerId = _activeDeviceId.value
        if (peerId != null) {
            conversationMessagesMap.remove(peerId)
            _pairedDevicesList.value = _pairedDevicesList.value.filter { it.id != peerId }
        }
        clearFileTransferState()
        _activeDeviceId.value = null
        networkService.disconnectFromPeer()
    }

    override fun disconnectAll() {
        clearConnectedSession(clearPairingRequests = true)
        networkService.disconnectFromPeer()
        stopSync()
    }

    override fun sendText(text: String) {
        val peerId = _activeDeviceId.value ?: return
        sendWirePayload(
            kind = KIND_TEXT,
            content = text,
            targetDeviceId = peerId,
            peerDeviceId = peerId,
            persistSent = true
        )
    }

    override fun offerFiles(files: List<PickedFile>) {
        val peerId = _activeDeviceId.value ?: return
        if (files.isEmpty()) return
        val offerId = generateUniqueId()
        pendingOutgoingFileBatches[offerId] = files
        val offer = FileOfferPayload(
            offerId = offerId,
            files = files.map {
                FilePreviewPayload(
                    fileName = it.name,
                    mimeType = it.mimeType,
                    fileSize = it.sizeBytes
                )
            }
        )
        sendWirePayload(
            kind = KIND_FILE_OFFER,
            content = SyncPayloadCodec.encodeFileOffer(offer),
            targetDeviceId = peerId,
            peerDeviceId = peerId
        )
    }

    override fun acceptFileOffer(offerId: String) {
        val offer = _incomingFileOffer.value?.takeIf { it.offerId == offerId } ?: return
        _incomingFileOffer.value = null
        _receivedFileBatch.value = null
        _fileTransferProgress.value = FileTransferProgress(
            peerName = offer.senderName,
            files = offer.files,
            percent = 0,
            direction = TransferDirection.RECEIVING
        )
        sendWirePayload(
            kind = KIND_FILE_ACCEPT,
            content = offerId,
            targetDeviceId = offer.senderDeviceId,
            peerDeviceId = offer.senderDeviceId
        )
    }

    override fun declineFileOffer(offerId: String) {
        val offer = _incomingFileOffer.value?.takeIf { it.offerId == offerId } ?: return
        _incomingFileOffer.value = null
        sendWirePayload(
            kind = KIND_FILE_REJECT,
            content = offerId,
            targetDeviceId = offer.senderDeviceId,
            peerDeviceId = offer.senderDeviceId
        )
    }

    override fun dismissReceivedFiles() {
        _receivedFileBatch.value = null
    }

    override suspend fun clearAllData() {
        clearConnectedSession(clearPairingRequests = true)
        networkService.disconnectFromPeer()
    }

    override fun deleteDevice(deviceId: String) {
        conversationMessagesMap.remove(deviceId)
        _pairedDevicesList.value = _pairedDevicesList.value.filter { it.id != deviceId }
        if (_activeDeviceId.value == deviceId) {
            _activeDeviceId.value = null
        }
    }

    private fun persistDevice(device: DeviceProfile) {
        deviceNameById[device.id] = device.name
        val current = _pairedDevicesList.value
        if (current.none { it.id == device.id }) {
            _pairedDevicesList.value = current + device
        } else {
            _pairedDevicesList.value = current.map { 
                if (it.id == device.id) device else it 
            }
        }
    }

    private fun handleIncomingPayload(json: String) {
        val payload = SyncPayloadCodec.decodeOrNull(json) ?: return
        deviceNameById[payload.originDeviceId] = payload.originDeviceName

        when (payload.kind) {
            KIND_CONNECT_REQUEST -> {
                val device = DeviceProfile(
                    id = payload.originDeviceId,
                    name = payload.originDeviceName,
                    type = parseDeviceType(payload.originDeviceType),
                    hostAddress = payload.content.takeIf { it.contains('.') },
                    isOnline = true
                )
                if (_pendingIncoming.value.none { it.id == device.id }) {
                    _pendingIncoming.value += device
                }
            }
            KIND_CONNECT_ACCEPT -> {
                _pendingOutgoing.value = null
                persistDevice(
                    DeviceProfile(
                        id = payload.originDeviceId,
                        name = payload.originDeviceName,
                        type = parseDeviceType(payload.originDeviceType),
                        isOnline = true
                    )
                )
                _activeDeviceId.value = payload.originDeviceId
            }
            KIND_CONNECT_REJECT -> {
                if (_activeDeviceId.value == payload.originDeviceId) {
                    _activeDeviceId.value = null
                    _pairedDevicesList.value = emptyList()
                    conversationMessagesMap.clear()
                    _currentMessagesList.value = emptyList()
                }
                networkService.disconnectFromPeer()
            }
            KIND_TEXT, "clipboard" -> {
                val peerId = payload.originDeviceId
                if (!payload.isForLocalDevice()) return
                persistMessage(
                    itemId = payload.messageId ?: generateUniqueId(),
                    peerDeviceId = peerId,
                    originDeviceId = payload.originDeviceId,
                    kind = KIND_TEXT,
                    mimeType = "text/plain",
                    content = payload.content,
                    timestamp = payload.timestamp
                )
                if (_activeDeviceId.value != peerId) {
                    _activeDeviceId.value = peerId
                    persistDevice(
                        DeviceProfile(
                            id = peerId,
                            name = payload.originDeviceName,
                            type = parseDeviceType(payload.originDeviceType),
                            isOnline = true
                        )
                    )
                }
                incomingNotifier.notifyIncoming(
                    senderName = payload.originDeviceName,
                    preview = payload.content.take(120),
                    isFile = false
                )
            }
            KIND_FILE_OFFER -> {
                val peerId = payload.originDeviceId
                if (!payload.isForLocalDevice()) return
                val offer = SyncPayloadCodec.decodeFileOfferOrNull(payload.content) ?: return
                _receivedFileBatch.value = null
                _fileTransferProgress.value = null
                _incomingFileOffer.value = IncomingFileOffer(
                    offerId = offer.offerId,
                    senderDeviceId = peerId,
                    senderName = payload.originDeviceName,
                    files = offer.files.map {
                        TransferFilePreview(
                            name = it.fileName,
                            mimeType = it.mimeType,
                            sizeBytes = it.fileSize
                        )
                    }
                )
                incomingNotifier.notifyIncoming(
                    senderName = payload.originDeviceName,
                    preview = "${offer.files.size} file(s) waiting for approval",
                    isFile = true
                )
            }
            KIND_FILE_ACCEPT -> {
                if (!payload.isForLocalDevice()) return
                val files = pendingOutgoingFileBatches.remove(payload.content) ?: return
                _fileTransferProgress.value = FileTransferProgress(
                    peerName = payload.originDeviceName,
                    files = files.map { TransferFilePreview(it.name, it.mimeType, it.sizeBytes) },
                    percent = 0,
                    direction = TransferDirection.SENDING
                )
                sendFileBatch(
                    offerId = payload.content,
                    files = files,
                    targetDeviceId = payload.originDeviceId
                )
            }
            KIND_FILE_REJECT -> {
                pendingOutgoingFileBatches.remove(payload.content)
            }
            KIND_FILE_BATCH -> {
                if (!payload.isForLocalDevice()) return
                val batch = SyncPayloadCodec.decodeFileBatchOrNull(payload.content) ?: return
                _receivedFileBatch.value = null
                saveIncomingFileBatch(
                    senderName = payload.originDeviceName,
                    files = batch.files
                )
            }
            KIND_FILE_TRANSFER_START -> {
                if (!payload.isForLocalDevice()) return
                val start = SyncPayloadCodec.decodeFileTransferStartOrNull(payload.content) ?: return
                startIncomingChunkedTransfer(payload.originDeviceName, start)
            }
            KIND_FILE_CHUNK -> {
                if (!payload.isForLocalDevice()) return
                val chunk = payload.decodeProtobufFileChunk()
                    ?: decodeCompactFileChunk(payload.content)
                    ?: SyncPayloadCodec.decodeFileChunkOrNull(payload.content)
                    ?: return
                receiveFileChunk(chunk)
            }
        }
    }

    private fun SyncPayload.isForLocalDevice(): Boolean =
        targetDeviceId == null || targetDeviceId == localDevice.id

    private fun clearConnectedSession(clearPairingRequests: Boolean = false) {
        _activeDeviceId.value = null
        _pairedDevicesList.value = emptyList()
        conversationMessagesMap.clear()
        _currentMessagesList.value = emptyList()
        if (clearPairingRequests) {
            _pendingOutgoing.value = null
            _pendingIncoming.value = emptyList()
        }
        clearFileTransferState()
    }

    private fun clearFileTransferState() {
        pendingOutgoingFileBatches.clear()
        clearIncomingAssemblies()
        _incomingFileOffer.value = null
        _fileTransferProgress.value = null
        _receivedFileBatch.value = null
    }

    private fun sendFileBatch(
        offerId: String,
        files: List<PickedFile>,
        targetDeviceId: String
    ) {
        val start = FileTransferStartPayload(
            offerId = offerId,
            files = files.map {
                FileTransferStartItem(
                    fileName = it.name,
                    mimeType = it.mimeType,
                    fileSize = it.sizeBytes,
                    totalChunks = chunkCount(it.sizeBytes)
                )
            }
        )
        sendWirePayload(
            kind = KIND_FILE_TRANSFER_START,
            content = SyncPayloadCodec.encodeFileTransferStart(start),
            targetDeviceId = targetDeviceId,
            peerDeviceId = targetDeviceId
        )

        val totalChunks = files.sumOf { chunkCount(it.sizeBytes) }.coerceAtLeast(1)
        var sentChunks = 0
        files.forEachIndexed { fileIndex, file ->
            var chunkIndex = 0
            var chunksSentForFile = 0
            val streamed = platformOperations.readFileChunks(file, FILE_CHUNK_SIZE_BYTES) { bytes ->
                val chunk = SyncBinaryChunk(
                    offerId = offerId,
                    fileIndex = fileIndex,
                    chunkIndex = chunkIndex,
                    bytes = bytes
                )
                sendFileChunk(chunk, targetDeviceId)
                sentChunks += 1
                chunksSentForFile += 1
                updateSendProgress(sentChunks, totalChunks)
                chunkIndex += 1
            }
            if (streamed && chunksSentForFile == 0 && file.sizeBytes == 0L) {
                val emptyFileChunk = SyncBinaryChunk(
                    offerId = offerId,
                    fileIndex = fileIndex,
                    chunkIndex = 0,
                    bytes = ByteArray(0)
                )
                sendFileChunk(emptyFileChunk, targetDeviceId)
                sentChunks += 1
                updateSendProgress(sentChunks, totalChunks)
            }
            if (!streamed) {
                updateSendProgress(sentChunks, totalChunks)
            }
        }

        scope.launch {
            delay(1200.milliseconds)
            _fileTransferProgress.value = null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun sendFileChunk(
        chunk: SyncBinaryChunk,
        targetDeviceId: String
    ) {
        sendWirePayload(
            kind = KIND_FILE_CHUNK,
            content = encodeFileChunkMetadata(chunk),
            targetDeviceId = targetDeviceId,
            peerDeviceId = targetDeviceId,
            binaryContent = chunk.bytes
        )
    }

    private fun encodeFileChunkMetadata(chunk: SyncBinaryChunk): String =
        "${chunk.offerId}|${chunk.fileIndex}|${chunk.chunkIndex}"

    @OptIn(ExperimentalEncodingApi::class)
    private fun SyncPayload.decodeProtobufFileChunk(): FileChunkPayload? {
        if (content.count { it == '|' } != 2) return null
        val first = content.indexOf('|')
        if (first <= 0) return null
        val second = content.indexOf('|', first + 1)
        if (second <= first) return null
        return FileChunkPayload(
            offerId = content.substring(0, first),
            fileIndex = content.substring(first + 1, second).toIntOrNull() ?: return null,
            chunkIndex = content.substring(second + 1).toIntOrNull() ?: return null,
            base64Data = Base64.encode(binaryContent)
        )
    }

    private fun decodeCompactFileChunk(content: String): FileChunkPayload? {
        val first = content.indexOf('|')
        if (first <= 0) return null
        val second = content.indexOf('|', first + 1)
        if (second <= first) return null
        val third = content.indexOf('|', second + 1)
        if (third <= second) return null
        return FileChunkPayload(
            offerId = content.substring(0, first),
            fileIndex = content.substring(first + 1, second).toIntOrNull() ?: return null,
            chunkIndex = content.substring(second + 1, third).toIntOrNull() ?: return null,
            base64Data = content.substring(third + 1)
        )
    }

    private fun chunkCount(size: Long): Int =
        (((size + FILE_CHUNK_SIZE_BYTES - 1) / FILE_CHUNK_SIZE_BYTES).toInt()).coerceAtLeast(1)

    private fun updateSendProgress(sentChunks: Int, totalChunks: Int) {
        val percent = ((sentChunks.toFloat() / totalChunks.toFloat()) * 100).toInt().coerceIn(1, 100)
        _fileTransferProgress.update { current -> current?.copy(percent = percent) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun saveIncomingFileBatch(senderName: String, files: List<FilePayload>) {
        val previews = files.map {
            TransferFilePreview(
                name = it.fileName,
                mimeType = it.mimeType,
                sizeBytes = it.fileSize
            )
        }
        val savedPaths = mutableListOf<String>()
        var remaining = files.size
        if (remaining == 0) return
        _fileTransferProgress.value = (_fileTransferProgress.value ?: FileTransferProgress(
            peerName = senderName,
            files = previews,
            percent = 0,
            direction = TransferDirection.RECEIVING
        )).copy(percent = 10)

        files.forEachIndexed { index, file ->
            val bytes = try {
                Base64.decode(file.base64Data)
            } catch (_: Exception) {
                null
            }
            if (bytes == null) {
                remaining -= 1
                updateReceiveProgress(files.size, index + 1)
                if (remaining == 0) {
                    completeReceivedTransfer(senderName, previews, savedPaths.toList())
                }
                return@forEachIndexed
            }
            _fileTransferProgress.update { current ->
                current?.copy(percent = ((index.toFloat() / files.size.toFloat()) * 80).toInt().coerceAtLeast(10))
            }
            platformOperations.saveFile(file.fileName, bytes) { success, savedPath ->
                if (success && savedPath != null) {
                    savedPaths += savedPath
                }
                remaining -= 1
                updateReceiveProgress(files.size, files.size - remaining)
                if (remaining == 0) {
                    completeReceivedTransfer(senderName, previews, savedPaths.toList())
                }
            }
        }
    }

    private fun completeReceivedTransfer(
        senderName: String,
        previews: List<TransferFilePreview>,
        savedPaths: List<String>
    ) {
        _fileTransferProgress.update { current -> current?.copy(percent = 100) }
        scope.launch {
            delay(900.milliseconds)
            _fileTransferProgress.value = null
            _receivedFileBatch.value = ReceivedFileBatch(
                senderName = senderName,
                files = previews,
                savedPaths = savedPaths
            )
        }
    }

    private fun startIncomingChunkedTransfer(
        senderName: String,
        start: FileTransferStartPayload
    ) {
        val previews = start.files.map {
            TransferFilePreview(
                name = it.fileName,
                mimeType = it.mimeType,
                sizeBytes = it.fileSize
            )
        }
        _receivedFileBatch.value = null
        _fileTransferProgress.value = FileTransferProgress(
            peerName = senderName,
            files = previews,
            percent = 1,
            direction = TransferDirection.RECEIVING
        )
        incomingAssemblies[start.offerId] = IncomingFileAssembly(
            senderName = senderName,
            files = start.files,
            writeHandles = start.files.map { platformOperations.beginFileWrite(it.fileName) }.toMutableList(),
            nextChunkIndexes = MutableList(start.files.size) { 0 },
            savedPaths = MutableList(start.files.size) { null },
            totalChunks = start.files.sumOf { it.totalChunks }.coerceAtLeast(1)
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun receiveFileChunk(chunk: FileChunkPayload) {
        receiveBinaryFileChunk(
            SyncBinaryChunk(
                offerId = chunk.offerId,
                fileIndex = chunk.fileIndex,
                chunkIndex = chunk.chunkIndex,
                bytes = try {
                    Base64.decode(chunk.base64Data)
                } catch (_: Exception) {
                    return
                }
            )
        )
    }

    private fun receiveBinaryFileChunk(chunk: SyncBinaryChunk) {
        val assembly = incomingAssemblies[chunk.offerId] ?: return
        val file = assembly.files.getOrNull(chunk.fileIndex) ?: return
        val handle = assembly.writeHandles.getOrNull(chunk.fileIndex) ?: run {
            incomingAssemblies.remove(chunk.offerId)
            cancelAssemblyWrites(assembly)
            _fileTransferProgress.value = null
            return
        }
        val nextChunkIndex = assembly.nextChunkIndexes.getOrNull(chunk.fileIndex) ?: return
        if (chunk.chunkIndex != nextChunkIndex || chunk.chunkIndex >= file.totalChunks) return
        if (!platformOperations.writeFileChunk(handle, chunk.bytes)) {
            incomingAssemblies.remove(chunk.offerId)
            cancelAssemblyWrites(assembly)
            _fileTransferProgress.value = null
            return
        }
        assembly.nextChunkIndexes[chunk.fileIndex] = nextChunkIndex + 1
        assembly.receivedChunks += 1
        val percent = ((assembly.receivedChunks.toFloat() / assembly.totalChunks.toFloat()) * 95)
            .toInt()
            .coerceIn(1, 95)
        _fileTransferProgress.update { current -> current?.copy(percent = percent) }

        if (assembly.nextChunkIndexes[chunk.fileIndex] >= file.totalChunks) {
            assembly.savedPaths[chunk.fileIndex] = platformOperations.finishFileWrite(handle)
            assembly.writeHandles[chunk.fileIndex] = null
        }

        if (assembly.receivedChunks >= assembly.totalChunks) {
            incomingAssemblies.remove(chunk.offerId)
            completeIncomingChunkedTransfer(assembly)
        }
    }

    private fun completeIncomingChunkedTransfer(assembly: IncomingFileAssembly) {
        val previews = assembly.files.map {
            TransferFilePreview(
                name = it.fileName,
                mimeType = it.mimeType,
                sizeBytes = it.fileSize
            )
        }
        completeReceivedTransfer(
            senderName = assembly.senderName,
            previews = previews,
            savedPaths = assembly.savedPaths.mapNotNull { it }
        )
    }

    private fun cancelAssemblyWrites(assembly: IncomingFileAssembly) {
        assembly.writeHandles.filterNotNull().forEach { handle ->
            platformOperations.cancelFileWrite(handle)
        }
        assembly.writeHandles.indices.forEach { index ->
            assembly.writeHandles[index] = null
        }
    }

    private fun clearIncomingAssemblies() {
        incomingAssemblies.values.forEach(::cancelAssemblyWrites)
        incomingAssemblies.clear()
    }

    private fun updateReceiveProgress(total: Int, completed: Int) {
        if (total <= 0) return
        val percent = ((completed.toFloat() / total.toFloat()) * 100).toInt().coerceIn(10, 100)
        _fileTransferProgress.update { current ->
            current?.copy(percent = percent)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun sendWirePayload(
        kind: String,
        content: String,
        targetDeviceId: String?,
        peerDeviceId: String,
        persistSent: Boolean = false,
        mimeType: String = "text/plain",
        displayContent: String = content,
        binaryContent: ByteArray = ByteArray(0)
    ) {
        val payload = SyncPayload(
            kind = kind,
            originDeviceId = localDevice.id,
            originDeviceName = localDevice.name,
            originDeviceType = localDevice.type.name,
            content = content,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            targetDeviceId = targetDeviceId,
            messageId = generateUniqueId(),
            binaryContent = binaryContent
        )
        val json = SyncPayloadCodec.encode(payload)
        networkService.sendToPeer(json)
        networkService.broadcastToClients(json)

        if (persistSent) {
            persistMessage(
                itemId = payload.messageId ?: generateUniqueId(),
                peerDeviceId = peerDeviceId,
                originDeviceId = localDevice.id,
                kind = kind,
                mimeType = mimeType,
                content = displayContent,
                timestamp = payload.timestamp
            )
        }
    }

    private fun persistMessage(
        itemId: String,
        peerDeviceId: String,
        originDeviceId: String,
        kind: String,
        mimeType: String,
        content: String,
        timestamp: Long
    ) {
        val isFile = kind == KIND_FILE_OFFER
        val message = SyncMessage(
            id = itemId,
            peerDeviceId = peerDeviceId,
            text = content,
            isFromMe = originDeviceId == localDevice.id,
            timestamp = timestamp,
            isFile = isFile,
            fileName = if (isFile) mimeType else null
        )

        val currentList = conversationMessagesMap[peerDeviceId] ?: emptyList()
        val updatedList = currentList + message
        conversationMessagesMap[peerDeviceId] = updatedList

        if (_activeDeviceId.value == peerDeviceId) {
            _currentMessagesList.value = updatedList
        }
    }

    private fun generateUniqueId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomPart = (1..16).map { chars.random() }.joinToString("")
        val timestamp = Clock.System.now().toEpochMilliseconds()
        return "$timestamp-$randomPart"
    }

    private fun parseDeviceType(value: String): DeviceType =
        runCatching { DeviceType.valueOf(value) }.getOrDefault(DeviceType.DESKTOP)

    companion object {
        const val DEFAULT_PORT = 8080
        const val KIND_CONNECT_REQUEST = "connect_request"
        const val KIND_CONNECT_ACCEPT = "connect_accept"
        const val KIND_CONNECT_REJECT = "connect_reject"
        const val KIND_TEXT = "text"
        const val KIND_FILE_OFFER = "file_offer"
        const val KIND_FILE_ACCEPT = "file_accept"
        const val KIND_FILE_REJECT = "file_reject"
        const val KIND_FILE_BATCH = "file_batch"
        const val KIND_FILE_TRANSFER_START = "file_transfer_start"
        const val KIND_FILE_CHUNK = "file_chunk"
        private const val FILE_CHUNK_SIZE_BYTES = 1024 * 1024
    }
}

private data class IncomingFileAssembly(
    val senderName: String,
    val files: List<FileTransferStartItem>,
    val writeHandles: MutableList<String?>,
    val nextChunkIndexes: MutableList<Int>,
    val savedPaths: MutableList<String?>,
    val totalChunks: Int,
    var receivedChunks: Int = 0
)
