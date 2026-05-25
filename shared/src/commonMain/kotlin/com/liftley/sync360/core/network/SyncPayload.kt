package com.liftley.sync360.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncPayload(
    val kind: String,
    val originDeviceId: String,
    val originDeviceName: String,
    val originDeviceType: String,
    val content: String,
    val timestamp: Long,
    val targetDeviceId: String? = null,
    val messageId: String? = null
)

@Serializable
data class FilePayload(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val base64Data: String
)

@Serializable
data class FileOfferPayload(
    val offerId: String,
    val files: List<FilePreviewPayload>
)

@Serializable
data class FilePreviewPayload(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long
)

@Serializable
data class FileBatchPayload(
    val offerId: String,
    val files: List<FilePayload>
)

@Serializable
data class FileTransferStartPayload(
    val offerId: String,
    val files: List<FileTransferStartItem>
)

@Serializable
data class FileTransferStartItem(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val totalChunks: Int
)

@Serializable
data class FileChunkPayload(
    val offerId: String,
    val fileIndex: Int,
    val chunkIndex: Int,
    val base64Data: String
)

object SyncPayloadCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: SyncPayload): String = json.encodeToString(SyncPayload.serializer(), payload)

    fun decodeOrNull(frame: String): SyncPayload? {
        return try {
            json.decodeFromString(SyncPayload.serializer(), frame)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeFile(file: FilePayload): String = json.encodeToString(FilePayload.serializer(), file)

    fun decodeFileOrNull(jsonStr: String): FilePayload? {
        return try {
            json.decodeFromString(FilePayload.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeFileOffer(offer: FileOfferPayload): String =
        json.encodeToString(FileOfferPayload.serializer(), offer)

    fun decodeFileOfferOrNull(jsonStr: String): FileOfferPayload? {
        return try {
            json.decodeFromString(FileOfferPayload.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeFileBatch(batch: FileBatchPayload): String =
        json.encodeToString(FileBatchPayload.serializer(), batch)

    fun decodeFileBatchOrNull(jsonStr: String): FileBatchPayload? {
        return try {
            json.decodeFromString(FileBatchPayload.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeFileTransferStart(start: FileTransferStartPayload): String =
        json.encodeToString(FileTransferStartPayload.serializer(), start)

    fun decodeFileTransferStartOrNull(jsonStr: String): FileTransferStartPayload? {
        return try {
            json.decodeFromString(FileTransferStartPayload.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeFileChunk(chunk: FileChunkPayload): String =
        json.encodeToString(FileChunkPayload.serializer(), chunk)

    fun decodeFileChunkOrNull(jsonStr: String): FileChunkPayload? {
        return try {
            json.decodeFromString(FileChunkPayload.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }
}
