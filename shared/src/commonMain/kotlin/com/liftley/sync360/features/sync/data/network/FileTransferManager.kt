package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.debug.agentDebugLog
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.data.network.api.FilePreviewDto
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileTransferManager(
    private val platformOperations: PlatformOperations,
    private val httpClient: HttpSyncClient
) {
    private val pendingOutgoingFileBatches = mutableMapOf<String, List<PickedFile>>()
    private val httpSentBytesByOffer = mutableMapOf<String, Long>()
    
    fun registerOutgoingFiles(offerId: String, files: List<PickedFile>) {
        pendingOutgoingFileBatches[offerId] = files
        httpSentBytesByOffer[offerId] = 0L
    }
    
    fun clearOutgoingFiles(offerId: String) {
        pendingOutgoingFileBatches.remove(offerId)
        httpSentBytesByOffer.remove(offerId)
    }

    fun getOutgoingFiles(offerId: String): List<PickedFile>? = pendingOutgoingFileBatches[offerId]

    fun getOutgoingFileSize(offerId: String, fileIndex: Int): Long? =
        pendingOutgoingFileBatches[offerId]?.getOrNull(fileIndex)?.sizeBytes

    suspend fun serveFileChunk(
        offerId: String, 
        fileIndex: Int, 
        chunkSizeBytes: Int, 
        onProgress: (percent: Int) -> Unit,
        onChunk: suspend (ByteArray) -> Unit
    ) {
        val files = pendingOutgoingFileBatches[offerId]
        val file = files?.getOrNull(fileIndex)
        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:serveFileChunk",
            message = "serve start",
            hypothesisId = "B",
            data = mapOf(
                "offerId" to offerId,
                "fileIndex" to fileIndex.toString(),
                "hasBatch" to (files != null).toString(),
                "fileName" to (file?.name ?: "null"),
                "fileSize" to (file?.sizeBytes?.toString() ?: "null")
            )
        )
        // #endregion
        if (files == null || file == null) return
        val totalBytes = files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var chunkCount = 0
        var bytesSent = 0L
        
        val readOk = withContext(Dispatchers.IO) {
            platformOperations.readFileChunks(file, chunkSizeBytes) { bytes ->
                onChunk(bytes)
                chunkCount++
                bytesSent += bytes.size

                val sent = (httpSentBytesByOffer[offerId] ?: 0L) + bytes.size
                httpSentBytesByOffer[offerId] = sent

                val percent = ((sent.toDouble() / totalBytes.toDouble()) * 95).toInt().coerceIn(1, 95)
                onProgress(percent)
                if (chunkCount == 1 || chunkCount % 20 == 0) {
                    // #region agent log
                    agentDebugLog(
                        location = "FileTransferManager.kt:serveFileChunk",
                        message = "serve progress",
                        hypothesisId = "E",
                        data = mapOf(
                            "offerId" to offerId,
                            "chunkCount" to chunkCount.toString(),
                            "bytesSent" to bytesSent.toString(),
                            "percent" to percent.toString()
                        ),
                        runId = "post-fix-3"
                    )
                    // #endregion
                }
            }
        }
        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:serveFileChunk",
            message = "serve end",
            hypothesisId = "E",
            data = mapOf(
                "offerId" to offerId,
                "fileIndex" to fileIndex.toString(),
                "readOk" to readOk.toString(),
                "chunkCount" to chunkCount.toString(),
                "bytesSent" to bytesSent.toString()
            )
        )
        // #endregion
    }
    
    suspend fun downloadFiles(
        serverIp: String,
        serverPort: Int,
        offerId: String,
        files: List<FilePreviewDto>,
        onProgress: (percent: Int) -> Unit
    ): List<String>? = withContext(Dispatchers.IO) {
        val savedPaths = MutableList(files.size) { null as String? }
        val totalBytes = files.sumOf { it.fileSize }.coerceAtLeast(1L)
        var writtenBytes = 0L
        var failed = false

        files.forEachIndexed { index, file ->
            if (failed) return@forEachIndexed
            val handle = platformOperations.beginFileWrite(file.fileName)
            if (handle == null) {
                // #region agent log
                agentDebugLog(
                    location = "FileTransferManager.kt:downloadFiles",
                    message = "beginFileWrite failed",
                    hypothesisId = "D",
                    data = mapOf("fileName" to file.fileName, "fileSize" to file.fileSize.toString())
                )
                // #endregion
                failed = true
                return@forEachIndexed
            }
            val beforeFileBytes = writtenBytes
            val downloadUrl = "http://$serverIp:$serverPort/files/$offerId/$index"
            // #region agent log
            agentDebugLog(
                location = "FileTransferManager.kt:downloadFiles",
                message = "file download loop start",
                hypothesisId = "A",
                data = mapOf(
                    "downloadUrl" to downloadUrl,
                    "expectedSize" to file.fileSize.toString(),
                    "fileName" to file.fileName
                )
            )
            // #endregion
            
            val downloaded = httpClient.downloadFileChunked(downloadUrl) { bytes ->
                val wrote = platformOperations.writeFileChunk(handle, bytes)
                if (wrote) {
                    writtenBytes += bytes.size
                    val percent = ((writtenBytes.toDouble() / totalBytes.toDouble()) * 95).toInt().coerceIn(1, 95)
                    onProgress(percent)
                }
                wrote
            }
            val fileWritten = writtenBytes - beforeFileBytes
            if (!downloaded || fileWritten != file.fileSize) {
                // #region agent log
                agentDebugLog(
                    location = "FileTransferManager.kt:downloadFiles",
                    message = "file download validation failed",
                    hypothesisId = "C",
                    data = mapOf(
                        "downloadUrl" to downloadUrl,
                        "downloaded" to downloaded.toString(),
                        "expectedSize" to file.fileSize.toString(),
                        "actualWritten" to fileWritten.toString()
                    )
                )
                // #endregion
                platformOperations.cancelFileWrite(handle)
                failed = true
                return@forEachIndexed
            }
            
            val savedPath = platformOperations.finishFileWrite(handle)
            if (savedPath == null) {
                // #region agent log
                agentDebugLog(
                    location = "FileTransferManager.kt:downloadFiles",
                    message = "finishFileWrite failed",
                    hypothesisId = "D",
                    data = mapOf("fileName" to file.fileName)
                )
                // #endregion
                failed = true
                return@forEachIndexed
            }
            savedPaths[index] = savedPath
        }

        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:downloadFiles",
            message = "download batch finished",
            hypothesisId = "C",
            data = mapOf(
                "offerId" to offerId,
                "failed" to failed.toString(),
                "savedCount" to savedPaths.count { it != null }.toString()
            )
        )
        // #endregion
        if (failed || savedPaths.any { it == null }) null else savedPaths.filterNotNull()
    }
}
