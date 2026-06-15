package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.security.SessionAuth
import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.core.security.SessionCrypto
import com.liftley.sync360.core.security.SessionReplayCache
import com.liftley.sync360.features.sync.data.network.api.ConnectAcceptDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRejectDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRequestDto
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.MessageDto
import com.liftley.sync360.features.sync.domain.model.DeviceProfile

internal class SessionAuthenticator(
    private val localDevice: DeviceProfile,
    private val localLanIp: String
) {
    private val replayCache = SessionReplayCache()

    fun newSessionToken(): String = SessionCrypto.secureToken()

    fun connectRequest(sessionToken: String): ConnectRequestDto {
        val auth = authFields(
            sessionToken = sessionToken,
            purpose = CONNECT_REQUEST,
            parts = listOf(localDevice.id, localDevice.name, localDevice.type.name, localLanIp)
        )
        return ConnectRequestDto(
            deviceId = localDevice.id,
            deviceName = localDevice.name,
            deviceType = localDevice.type.name,
            senderIp = localLanIp,
            sessionToken = sessionToken,
            issuedAtMillis = auth.issuedAtMillis,
            nonce = auth.nonce,
            signature = auth.signature
        )
    }

    fun connectAccept(sessionToken: String): ConnectAcceptDto {
        val auth = authFields(
            sessionToken = sessionToken,
            purpose = CONNECT_ACCEPT,
            parts = listOf(localDevice.id, localDevice.name, localDevice.type.name, localLanIp)
        )
        return ConnectAcceptDto(
            deviceId = localDevice.id,
            deviceName = localDevice.name,
            deviceType = localDevice.type.name,
            senderIp = localLanIp,
            sessionToken = sessionToken,
            issuedAtMillis = auth.issuedAtMillis,
            nonce = auth.nonce,
            signature = auth.signature
        )
    }

    fun connectReject(sessionToken: String?): ConnectRejectDto {
        if (sessionToken == null) {
            return ConnectRejectDto(senderDeviceId = localDevice.id)
        }

        val auth = authFields(
            sessionToken = sessionToken,
            purpose = CONNECT_REJECT,
            parts = listOf(localDevice.id)
        )
        return ConnectRejectDto(
            senderDeviceId = localDevice.id,
            sessionToken = sessionToken,
            issuedAtMillis = auth.issuedAtMillis,
            nonce = auth.nonce,
            signature = auth.signature
        )
    }

    fun signTextMessage(message: MessageDto): MessageDto {
        val auth = authFields(
            sessionToken = message.sessionToken,
            purpose = TEXT_MESSAGE,
            parts = textMessageAuthParts(message)
        )
        return message.copy(
            issuedAtMillis = auth.issuedAtMillis,
            nonce = auth.nonce,
            signature = auth.signature
        )
    }

    fun verifyConnectRequest(request: ConnectRequestDto): Boolean {
        return verifyAuth(
            fields = request.authFields(),
            sessionToken = request.sessionToken,
            purpose = CONNECT_REQUEST,
            parts = listOf(request.deviceId, request.deviceName, request.deviceType, request.senderIp)
        )
    }

    fun verifyConnectAccept(accept: ConnectAcceptDto): Boolean {
        return verifyAuth(
            fields = accept.authFields(),
            sessionToken = accept.sessionToken,
            purpose = CONNECT_ACCEPT,
            parts = listOf(accept.deviceId, accept.deviceName, accept.deviceType, accept.senderIp)
        )
    }

    fun verifyConnectReject(reject: ConnectRejectDto): Boolean {
        val sessionToken = reject.sessionToken ?: return false
        return verifyAuth(
            fields = reject.authFields(),
            sessionToken = sessionToken,
            purpose = CONNECT_REJECT,
            parts = listOf(reject.senderDeviceId)
        )
    }

    fun verifyTextMessage(message: MessageDto): Boolean {
        return verifyAuth(
            fields = message.authFields(),
            sessionToken = message.sessionToken,
            purpose = TEXT_MESSAGE,
            parts = textMessageAuthParts(message)
        )
    }

    fun verifyFileOffer(offer: FileOfferDto): Boolean {
        return verifyAuth(
            fields = offer.authFields(),
            sessionToken = offer.sessionToken,
            purpose = FILE_OFFER,
            parts = fileOfferAuthParts(offer)
        )
    }

    fun verifyFileComplete(complete: FileCompleteDto): Boolean {
        return verifyAuth(
            fields = complete.authFields(),
            sessionToken = complete.sessionToken,
            purpose = FILE_COMPLETE,
            parts = listOf(complete.offerId, complete.senderDeviceId)
        )
    }

    fun verifyFileUpload(
        sessionToken: String,
        authFields: SessionAuthFields,
        offerId: String,
        fileIndex: Int
    ): Boolean {
        return verifyAuth(
            fields = authFields,
            sessionToken = sessionToken,
            purpose = FILE_UPLOAD,
            parts = listOf(offerId, fileIndex.toString())
        )
    }

    fun clearReplayHistory() {
        replayCache.clear()
    }

    private fun authFields(sessionToken: String, purpose: String, parts: List<String>): SessionAuthFields {
        return SessionAuth.create(sessionToken, purpose, parts)
    }

    private fun verifyAuth(
        fields: SessionAuthFields,
        sessionToken: String,
        purpose: String,
        parts: List<String>
    ): Boolean {
        return SessionAuth.verify(fields, sessionToken, purpose, parts, replayCache)
    }

    private fun textMessageAuthParts(message: MessageDto): List<String> {
        return listOf(
            message.messageId,
            message.senderDeviceId,
            message.senderName,
            message.content,
            message.timestamp.toString()
        )
    }

    private fun fileOfferAuthParts(offer: FileOfferDto): List<String> {
        return listOf(offer.offerId, offer.senderDeviceId, offer.senderName) +
            offer.files.flatMap { file -> listOf(file.fileName, file.mimeType, file.fileSize.toString()) }
    }

    private companion object {
        const val CONNECT_REQUEST = "connect_request"
        const val CONNECT_ACCEPT = "connect_accept"
        const val CONNECT_REJECT = "connect_reject"
        const val TEXT_MESSAGE = "text_message"
        const val FILE_OFFER = "file_offer"
        const val FILE_COMPLETE = "file_complete"
        const val FILE_UPLOAD = "file_upload"
    }
}

private fun ConnectRequestDto.authFields(): SessionAuthFields =
    SessionAuthFields(issuedAtMillis, nonce, signature)

private fun ConnectAcceptDto.authFields(): SessionAuthFields =
    SessionAuthFields(issuedAtMillis, nonce, signature)

private fun ConnectRejectDto.authFields(): SessionAuthFields =
    SessionAuthFields(issuedAtMillis, nonce, signature)

private fun MessageDto.authFields(): SessionAuthFields =
    SessionAuthFields(issuedAtMillis, nonce, signature)

private fun FileOfferDto.authFields(): SessionAuthFields =
    SessionAuthFields(issuedAtMillis, nonce, signature)

private fun FileCompleteDto.authFields(): SessionAuthFields =
    SessionAuthFields(issuedAtMillis, nonce, signature)
