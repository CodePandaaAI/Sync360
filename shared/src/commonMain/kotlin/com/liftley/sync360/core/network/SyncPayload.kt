@file:OptIn(ExperimentalSerializationApi::class)

package com.liftley.sync360.core.network

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class SyncPayload(
    @ProtoNumber(1)
    val kind: String,
    @ProtoNumber(2)
    val originDeviceId: String,
    @ProtoNumber(3)
    val originDeviceName: String,
    @ProtoNumber(4)
    val originDeviceType: String,
    @ProtoNumber(5)
    val content: String,
    @ProtoNumber(6)
    val timestamp: Long,
    @ProtoNumber(7)
    val targetDeviceId: String? = null,
    @ProtoNumber(8)
    val messageId: String? = null,
    @ProtoNumber(9)
    val binaryContent: ByteArray = ByteArray(0)
)

@Serializable
data class FilePayload(
    @ProtoNumber(1)
    val fileName: String,
    @ProtoNumber(2)
    val mimeType: String,
    @ProtoNumber(3)
    val fileSize: Long,
    @ProtoNumber(4)
    val base64Data: String
)

@Serializable
data class FileOfferPayload(
    @ProtoNumber(1)
    val offerId: String,
    @ProtoNumber(2)
    val files: List<FilePreviewPayload>
)

@Serializable
data class FilePreviewPayload(
    @ProtoNumber(1)
    val fileName: String,
    @ProtoNumber(2)
    val mimeType: String,
    @ProtoNumber(3)
    val fileSize: Long
)

@Serializable
data class FileBatchPayload(
    @ProtoNumber(1)
    val offerId: String,
    @ProtoNumber(2)
    val files: List<FilePayload>
)

@Serializable
data class FileTransferStartPayload(
    @ProtoNumber(1)
    val offerId: String,
    @ProtoNumber(2)
    val files: List<FileTransferStartItem>
)

@Serializable
data class FileTransferStartItem(
    @ProtoNumber(1)
    val fileName: String,
    @ProtoNumber(2)
    val mimeType: String,
    @ProtoNumber(3)
    val fileSize: Long,
    @ProtoNumber(4)
    val totalChunks: Int
)

@Serializable
data class FileChunkPayload(
    @ProtoNumber(1)
    val offerId: String,
    @ProtoNumber(2)
    val fileIndex: Int,
    @ProtoNumber(3)
    val chunkIndex: Int,
    @ProtoNumber(4)
    val base64Data: String
)

@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
object SyncPayloadCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val protobuf = ProtoBuf {
        encodeDefaults = true
    }

    fun encode(payload: SyncPayload): String =
        PROTOBUF_FRAME_PREFIX + Base64.encode(
            protobuf.encodeToByteArray(SyncPayload.serializer(), payload)
        )

    fun decodeOrNull(frame: String): SyncPayload? {
        if (frame.startsWith(PROTOBUF_FRAME_PREFIX)) {
            return try {
                protobuf.decodeFromByteArray(
                    SyncPayload.serializer(),
                    Base64.decode(frame.removePrefix(PROTOBUF_FRAME_PREFIX))
                )
            } catch (_: Exception) {
                null
            }
        }
        return try {
            json.decodeFromString(SyncPayload.serializer(), frame)
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

    fun decodeFileChunkOrNull(jsonStr: String): FileChunkPayload? {
        return try {
            json.decodeFromString(FileChunkPayload.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    private const val PROTOBUF_FRAME_PREFIX = "pb:"
}
