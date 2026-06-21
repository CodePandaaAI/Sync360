package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.BackgroundServiceStartResult
import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.core.platform.NetworkEnvironment
import com.liftley.sync360.core.platform.PlatformFileError
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.SyncForegroundServiceStatus
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.SendItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RawTcpFileTransferTest {
    @Test
    fun sendsOneFileThroughRawTransport() = runBlocking {
        val sender = RecordingRawSender()
        val manager = TransferPayloadStore(FakePlatformOperations)
        val file = pickedFile(index = 0, size = 2_500_000L)
        val progress = mutableListOf<Long>()

        val result = manager.uploadOutgoingFilesRaw(
            rawTransport = sender,
            serverIp = "192.168.1.2",
            serverPort = 49152,
            offerId = "offer-one",
            transferToken = "a".repeat(64),
            files = listOf(file),
            onProgress = progress::add
        )

        assertIs<HttpTransportResult.Success>(result)
        assertEquals(listOf(0), sender.headers.map { it.fileIndex })
        assertEquals(listOf(file.sizeBytes), sender.headers.map { it.contentLength })
        assertEquals(listOf("file-0.bin"), sender.headers.map { it.fileIdentifier })
        assertEquals(listOf(file.sizeBytes), progress)
    }

    @Test
    fun sendsMultipleFilesInIndexOrderThroughRawTransport() = runBlocking {
        val sender = RecordingRawSender()
        val manager = TransferPayloadStore(FakePlatformOperations)
        val files = listOf(
            pickedFile(index = 0, size = 1_000_000L),
            pickedFile(index = 1, size = 2_000_000L),
            pickedFile(index = 2, size = 3_000_000L)
        )
        val progress = mutableListOf<Long>()

        val result = manager.uploadOutgoingFilesRaw(
            rawTransport = sender,
            serverIp = "192.168.1.2",
            serverPort = 49153,
            offerId = "offer-many",
            transferToken = "b".repeat(64),
            files = files,
            onProgress = progress::add
        )

        assertIs<HttpTransportResult.Success>(result)
        assertEquals(listOf(0, 1, 2), sender.headers.map { it.fileIndex })
        assertEquals(files.map { it.sizeBytes }, sender.headers.map { it.contentLength })
        assertEquals(files.sumOf { it.sizeBytes }, sender.totalBytesSent)
        assertEquals(listOf(1_000_000L, 3_000_000L, 6_000_000L), progress)
    }

    @Test
    fun transferTokenIsSingleUsePerFileIndex() {
        val store = RawTransferGrantStore(nowMillis = { 1_000L })
        store.register("transfer", "token", fileCount = 2)

        assertTrue(store.validateAndConsume("transfer", "token", 0))
        assertTrue(!store.validateAndConsume("transfer", "token", 0))
        assertTrue(store.validateAndConsume("transfer", "token", 1))
    }

    @Test
    fun wrongExpiredAndRevokedTokensAreRejected() {
        var now = 1_000L
        val store = RawTransferGrantStore(nowMillis = { now })
        store.register("transfer", "token", fileCount = 1)

        assertTrue(!store.validateAndConsume("transfer", "wrong", 0))
        now += RawTcpFileTransferConfig.TOKEN_TTL_MILLIS + 1
        assertTrue(!store.validateAndConsume("transfer", "token", 0))

        store.register("retry-transfer", "retry-token", fileCount = 1)
        store.revoke("retry-transfer")
        assertTrue(!store.validateAndConsume("retry-transfer", "retry-token", 0))

        store.register("retry-transfer", "replacement-token", fileCount = 1)
        assertTrue(store.validateAndConsume("retry-transfer", "replacement-token", 0))
    }

    @Test
    fun partialIncomingTransferDeletesTemporaryFile() {
        val platform = RecordingWritePlatform()
        val manager = TransferPayloadStore(platform)

        assertTrue(manager.initIncomingFileWrite("partial", 0, "partial.bin", "application/octet-stream", 10, "0".repeat(64)))
        assertTrue(manager.writeIncomingFileChunk("partial", 0, byteArrayOf(1, 2, 3), 0, 3))
        assertNull(manager.completeIncomingFileWrite("partial", 0))
        assertEquals(1, platform.cancelCount)
    }

    @Test
    fun hashMismatchDeletesTemporaryFile() {
        val platform = RecordingWritePlatform()
        val manager = TransferPayloadStore(platform)
        val bytes = byteArrayOf(1, 2, 3)

        assertTrue(manager.initIncomingFileWrite("hash", 0, "hash.bin", "application/octet-stream", bytes.size.toLong(), "0".repeat(64)))
        assertTrue(manager.writeIncomingFileChunk("hash", 0, bytes, 0, bytes.size))
        assertNull(manager.completeIncomingFileWrite("hash", 0))
        assertEquals(1, platform.cancelCount)
    }

    @Test
    fun cancellationDeletesAllTemporaryFiles() {
        val platform = RecordingWritePlatform()
        val manager = TransferPayloadStore(platform)

        assertTrue(manager.initIncomingFileWrite("cancel", 0, "one.bin", "application/octet-stream", 1, "0".repeat(64)))
        assertTrue(manager.initIncomingFileWrite("cancel", 1, "two.bin", "application/octet-stream", 1, "0".repeat(64)))
        manager.cancelAllIncomingWrites()

        assertEquals(2, platform.cancelCount)
    }

    private fun pickedFile(index: Int, size: Long): PickedFile =
        PickedFile(
            id = "file-$index",
            name = "file-$index.bin",
            mimeType = "application/octet-stream",
            sizeBytes = size
        )
}

private class RecordingRawSender : RawFileByteSender {
    val headers = mutableListOf<RawTcpFileHeader>()
    var totalBytesSent = 0L

    override suspend fun send(
        host: String,
        port: Int,
        header: RawTcpFileHeader,
        file: PickedFile,
        platformOperations: PlatformOperations,
        onBytesSent: (Long) -> Unit
    ): RawTcpSendResult {
        headers += header
        totalBytesSent += file.sizeBytes
        onBytesSent(file.sizeBytes)
        return RawTcpSendResult.Success(file.sizeBytes)
    }

    override suspend fun send(
        host: String,
        port: Int,
        header: RawTcpFileHeader,
        item: SendItem,
        platformOperations: PlatformOperations,
        onBytesSent: (Long) -> Unit
    ): RawTcpSendResult {
        headers += header
        totalBytesSent += item.sizeBytes
        onBytesSent(item.sizeBytes)
        return RawTcpSendResult.Success(item.sizeBytes)
    }
}

private object FakePlatformOperations : PlatformOperations {
    override fun startForegroundService(status: SyncForegroundServiceStatus): BackgroundServiceStartResult =
        BackgroundServiceStartResult.NOT_REQUIRED

    override fun updateForegroundService(status: SyncForegroundServiceStatus) = Unit

    override fun startTransferService(): BackgroundServiceStartResult =
        BackgroundServiceStartResult.NOT_REQUIRED

    override fun stopService() = Unit

    override fun readClipboard(): String? = null

    override fun writeClipboard(text: String) = Unit

    override fun openFilePicker(
        kind: FilePickerKind,
        onFilesSelected: (files: List<PickedFile>) -> Unit
    ) = Unit

    override suspend fun readFileChunks(
        file: PickedFile,
        chunkSizeBytes: Int,
        onChunk: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ): FileOperationResult<Long> =
        FileOperationResult.Failure(PlatformFileError.SOURCE_UNAVAILABLE)

    override fun beginFileWrite(name: String): FileOperationResult<String> =
        FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)

    override fun getAvailableStorageBytes(): FileOperationResult<Long> =
        FileOperationResult.Success(Long.MAX_VALUE)

    override fun writeFileChunk(
        handle: String,
        bytes: ByteArray,
        offset: Int,
        length: Int
    ): FileOperationResult<Int> =
        FileOperationResult.Failure(PlatformFileError.WRITE_FAILED)

    override fun finishFileWrite(handle: String): FileOperationResult<String> =
        FileOperationResult.Failure(PlatformFileError.FINALIZE_FAILED)

    override fun cancelFileWrite(handle: String): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun deleteFile(path: String): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun openFile(path: String): FileOperationResult<Unit> =
        FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)

    override fun showFileInFolder(path: String): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun openDownloadsFolder(): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun getNetworkEnvironment(): NetworkEnvironment = NetworkEnvironment.Unavailable
}

private class RecordingWritePlatform : PlatformOperations {
    var cancelCount = 0
    private var nextHandle = 0

    override fun startForegroundService(status: SyncForegroundServiceStatus): BackgroundServiceStartResult =
        BackgroundServiceStartResult.NOT_REQUIRED

    override fun updateForegroundService(status: SyncForegroundServiceStatus) = Unit

    override fun startTransferService(): BackgroundServiceStartResult =
        BackgroundServiceStartResult.NOT_REQUIRED

    override fun stopService() = Unit
    override fun readClipboard(): String? = null
    override fun writeClipboard(text: String) = Unit
    override fun openFilePicker(
        kind: FilePickerKind,
        onFilesSelected: (files: List<PickedFile>) -> Unit
    ) = Unit

    override suspend fun readFileChunks(
        file: PickedFile,
        chunkSizeBytes: Int,
        onChunk: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ): FileOperationResult<Long> =
        FileOperationResult.Failure(PlatformFileError.SOURCE_UNAVAILABLE)

    override fun beginFileWrite(name: String): FileOperationResult<String> =
        FileOperationResult.Success("handle-${nextHandle++}")

    override fun getAvailableStorageBytes(): FileOperationResult<Long> =
        FileOperationResult.Success(Long.MAX_VALUE)

    override fun writeFileChunk(
        handle: String,
        bytes: ByteArray,
        offset: Int,
        length: Int
    ): FileOperationResult<Int> = FileOperationResult.Success(length)

    override fun finishFileWrite(handle: String): FileOperationResult<String> =
        FileOperationResult.Success(handle)

    override fun cancelFileWrite(handle: String): FileOperationResult<Unit> {
        cancelCount += 1
        return FileOperationResult.Success(Unit)
    }

    override fun deleteFile(path: String): FileOperationResult<Unit> {
        cancelCount += 1
        return FileOperationResult.Success(Unit)
    }

    override fun openFile(path: String): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun showFileInFolder(path: String): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun openDownloadsFolder(): FileOperationResult<Unit> =
        FileOperationResult.Success(Unit)

    override fun getNetworkEnvironment(): NetworkEnvironment = NetworkEnvironment.Unavailable
}