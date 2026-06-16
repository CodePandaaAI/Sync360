package com.liftley.sync360.core.platform

interface PlatformOperations :
    BackgroundServiceOperations,
    ClipboardOperations,
    FileOperations,
    NetworkEnvironmentProvider

interface BackgroundServiceOperations {
    fun startTransferService(): BackgroundServiceStartResult
    fun stopService()
}

interface ClipboardOperations {
    fun readClipboard(): String?
    fun writeClipboard(text: String)
}

interface FileOperations {
    fun openFilePicker(kind: FilePickerKind, onFilesSelected: (files: List<com.liftley.sync360.features.sync.domain.model.PickedFile>) -> Unit)
    suspend fun readFileChunks(
        file: com.liftley.sync360.features.sync.domain.model.PickedFile,
        chunkSizeBytes: Int,
        onChunk: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ): FileOperationResult<Long>
    fun beginFileWrite(name: String): FileOperationResult<String>
    fun getAvailableStorageBytes(): FileOperationResult<Long>
    fun writeFileChunk(handle: String, bytes: ByteArray, offset: Int, length: Int): FileOperationResult<Int>
    fun finishFileWrite(handle: String): FileOperationResult<String>
    fun cancelFileWrite(handle: String): FileOperationResult<Unit>
    fun deleteFile(path: String): FileOperationResult<Unit>
    fun openFile(path: String): FileOperationResult<Unit>
    fun showFileInFolder(path: String): FileOperationResult<Unit>
    fun openDownloadsFolder(): FileOperationResult<Unit>
}

interface NetworkEnvironmentProvider {
    fun getNetworkEnvironment(): NetworkEnvironment
}

enum class BackgroundServiceStartResult {
    STARTED,
    STARTED_WITH_NOTIFICATION_BLOCKED,
    NOT_REQUIRED,
    FAILED
}

data class NetworkEnvironment(
    val addresses: List<LocalNetworkAddress>
) {
    val preferredAddress: String
        get() = addresses.firstOrNull()?.address ?: "127.0.0.1"

    val addressSet: Set<String>
        get() = addresses.mapTo(linkedSetOf()) { it.address }

    val isAvailable: Boolean
        get() = addresses.isNotEmpty()

    fun addressForPeer(peerHost: String): String {
        val peer = peerHost.toIpv4Bytes() ?: return preferredAddress
        return addresses
            .mapNotNull { candidate ->
                val local = candidate.address.toIpv4Bytes() ?: return@mapNotNull null
                val sharedPrefix = sharedPrefixBits(local, peer)
                candidate.address to sharedPrefix
            }
            .filter { (_, sharedPrefix) -> sharedPrefix >= MIN_ROUTE_PREFIX_BITS }
            .maxByOrNull { (_, sharedPrefix) -> sharedPrefix }
            ?.first
            ?: preferredAddress
    }

    companion object {
        val Unavailable = NetworkEnvironment(emptyList())
        private const val MIN_ROUTE_PREFIX_BITS = 16
    }
}

data class LocalNetworkAddress(
    val address: String,
    val interfaceName: String,
    val kind: NetworkInterfaceKind
)

enum class NetworkInterfaceKind {
    WIFI,
    ETHERNET,
    HOTSPOT,
    VPN,
    OTHER
}

private fun String.toIpv4Bytes(): List<Int>? {
    val parts = split('.')
    if (parts.size != 4) return null
    return parts.map { part ->
        if (part.isEmpty() || part.any { !it.isDigit() }) return null
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
}

private fun sharedPrefixBits(first: List<Int>, second: List<Int>): Int {
    var bits = 0
    for (index in first.indices) {
        val difference = first[index] xor second[index]
        if (difference == 0) {
            bits += 8
            continue
        }
        bits += difference.countLeadingZeroBits() - (Int.SIZE_BITS - 8)
        break
    }
    return bits
}

sealed interface FileOperationResult<out T> {
    data class Success<T>(val value: T) : FileOperationResult<T>
    data class Failure(val error: PlatformFileError) : FileOperationResult<Nothing>
}

enum class PlatformFileError {
    SOURCE_UNAVAILABLE,
    DESTINATION_UNAVAILABLE,
    STORAGE_FULL,
    INVALID_HANDLE,
    READ_FAILED,
    WRITE_FAILED,
    FINALIZE_FAILED,
    CANCEL_FAILED,
    DELETE_FAILED,
    OPEN_FAILED
}
