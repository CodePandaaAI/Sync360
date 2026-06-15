package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.features.sync.data.network.FileSendFailure
import com.liftley.sync360.features.sync.data.network.FileSendResult
import com.liftley.sync360.features.sync.data.network.IncomingUploadFailure
import com.liftley.sync360.features.sync.data.network.OutgoingFileTransferCoordinator
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFailureReason
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import com.liftley.sync360.features.sync.domain.model.TransferStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class TransferEngine(
    private val scope: CoroutineScope,
    private val incomingNotifier: IncomingMessageNotifier,
    private val platformOperations: PlatformOperations,
    private val deviceSession: DeviceSessionStore,
    private val deviceRegistry: DeviceRegistry,
    private val incoming: IncomingFileTransferCoordinator,
    private val outgoing: OutgoingFileTransferCoordinator
) {
    private val store = TransferStore()
    private var outgoingJob: Job? = null
    private var incomingTimeoutJob: Job? = null

    val snapshot: Flow<TransferSnapshot> = store.snapshot
    val isActive: Boolean
        get() = store.value.isActive

    fun offer(files: List<PickedFile>) {
        if (isActive) {
            fail("A transfer is already in progress", TransferDirection.SENDING)
            return
        }
        if (files.isEmpty() || files.size > SyncProtocolLimits.MAX_FILES_PER_TRANSFER) {
            fail(
                "Select up to ${SyncProtocolLimits.MAX_FILES_PER_TRANSFER} files",
                TransferDirection.SENDING
            )
            return
        }
        if (files.any {
                it.sizeBytes < 0L ||
                    it.sizeBytes > SyncProtocolLimits.MAX_FILE_BYTES ||
                    it.name.isBlank() ||
                    it.name.length > SyncProtocolLimits.MAX_FILE_NAME_LENGTH ||
                    it.mimeType.length > SyncProtocolLimits.MAX_MIME_TYPE_LENGTH
            }
        ) {
            fail("One or more selected files are invalid", TransferDirection.SENDING)
            return
        }
        var totalBytes = 0L
        for (file in files) {
            if (
                file.sizeBytes > Long.MAX_VALUE - totalBytes ||
                totalBytes + file.sizeBytes > SyncProtocolLimits.MAX_BATCH_BYTES
            ) {
                fail("Selected files are too large", TransferDirection.SENDING)
                return
            }
            totalBytes += file.sizeBytes
        }

        val peerId = deviceSession.activeDeviceIdValue ?: return
        val route = deviceRegistry.routeFor(peerId) ?: return
        val sessionToken = deviceRegistry.sessionTokenFor(peerId) ?: return
        val offerId = newSyncItemId()

        outgoingJob?.cancel()
        outgoingJob = scope.launch {
            startService()
            store.start(
                FileTransferProgress(
                    peerName = "Peer",
                    files = outgoing.previews(files),
                    percent = 1,
                    direction = TransferDirection.SENDING,
                    stage = TransferStage.PREPARING
                )
            )

            val result = outgoing.sendFiles(
                peerHost = route.host,
                peerPort = route.port,
                offerId = offerId,
                files = files,
                sessionToken = sessionToken,
                onProgress = {
                    updateStage(TransferStage.TRANSFERRING)
                    updateProgress(it)
                },
                onVerifying = {
                    updateStage(TransferStage.VERIFYING)
                    updateProgress(99)
                }
            )

            if (result is FileSendResult.Success) {
                updateProgress(100)
                delay(900.milliseconds)
                store.succeed(TransferDirection.SENDING)
            } else {
                val reason = (result as FileSendResult.Failure).reason.toFailureReason()
                fail(reason.defaultMessage(), TransferDirection.SENDING, reason = reason)
            }
            stopService()
            outgoingJob = null
        }
    }

    fun dismissReceivedFiles() = store.dismissReceivedBatch()

    fun dismissFailure() = store.dismissFailure()

    fun receiveOffer(offer: FileOfferDto, remoteHost: String): Boolean {
        val start = incoming.startOffer(
            offer = offer,
            isApprovedSession = isApprovedPeerAtRoute(
                offer.senderDeviceId,
                offer.sessionToken,
                remoteHost
            ),
            hasActiveTransfer = isActive,
            onProgress = ::updateProgress
        ) ?: return false

        startService()
        resetIncomingTimeout()
        store.start(start.progress)
        incomingNotifier.notifyIncoming(
            start.senderName,
            "Receiving ${start.fileCount} files...",
            true
        )
        return true
    }

    fun receiveComplete(complete: FileCompleteDto, remoteHost: String): Boolean {
        val accepted = incoming.completeSignal(
            complete = complete,
            isApprovedSession = isApprovedPeerAtRoute(
                complete.senderDeviceId,
                complete.sessionToken,
                remoteHost
            )
        )
        if (!accepted) return false

        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = null
        updateProgress(100)
        scope.launch {
            delay(900.milliseconds)
            store.succeed(TransferDirection.RECEIVING)
            stopService()
        }
        return true
    }

    fun initIncomingFile(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields,
        declaredLength: Long,
        remoteHost: String
    ): Boolean {
        if (!isApprovedTokenAtRoute(sessionToken, remoteHost)) return false
        val accepted = incoming.initFileWrite(
            offerId,
            fileIndex,
            sessionToken,
            authFields,
            declaredLength
        )
        if (accepted) resetIncomingTimeout()
        return accepted
    }

    fun receiveChunk(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean {
        updateStage(TransferStage.TRANSFERRING)
        val written = incoming.writeChunk(offerId, fileIndex, chunk)
        if (written) resetIncomingTimeout()
        return written
    }

    fun completeIncomingFile(offerId: String, fileIndex: Int): String? {
        updateStage(TransferStage.VERIFYING)
        updateProgress(99)
        val complete = incoming.completeFileWrite(offerId, fileIndex)
        if (complete.batch != null) {
            incomingTimeoutJob?.cancel()
            incomingTimeoutJob = null
            store.succeed(TransferDirection.RECEIVING, complete.batch)
            stopService()
        }
        return complete.savedPath
    }

    fun failIncomingFile(
        offerId: String,
        fileIndex: Int,
        knownFailure: IncomingUploadFailure?
    ): IncomingUploadFailure? {
        val failure = knownFailure ?: incoming.consumeFileWriteFailure(offerId, fileIndex)
        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = null
        incoming.errorFileWrite(offerId, fileIndex)
        val reason = failure.toFailureReason()
        fail(reason.defaultMessage(), TransferDirection.RECEIVING, reason = reason)
        stopService()
        return failure
    }

    fun consumeIncomingFailure(
        offerId: String,
        fileIndex: Int
    ): IncomingUploadFailure? = incoming.consumeFileWriteFailure(offerId, fileIndex)

    fun cancel(updateService: Boolean = true) {
        outgoingJob?.cancel()
        outgoingJob = null
        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = null
        incoming.clear()
        store.clear()
        if (updateService) stopService()
    }

    private fun isApprovedPeerAtRoute(
        deviceId: String,
        sessionToken: String,
        remoteHost: String
    ): Boolean {
        val session = deviceRegistry.sessionFor(deviceId) ?: return false
        return session.sessionToken == sessionToken && session.route.host == remoteHost
    }

    private fun isApprovedTokenAtRoute(sessionToken: String, remoteHost: String): Boolean {
        return deviceRegistry.approvedSessions.value.any {
            it.sessionToken == sessionToken && it.route.host == remoteHost
        }
    }

    private fun updateProgress(percent: Int) {
        val current = store.value.progress
        if (current != null && current.percent != percent) store.updateProgress(percent)
    }

    private fun updateStage(stage: TransferStage) {
        val current = store.value.progress
        if (current != null && current.stage != stage) store.updateStage(stage)
    }

    private fun fail(
        message: String,
        direction: TransferDirection,
        failedFileName: String? = null,
        reason: TransferFailureReason = TransferFailureReason.UNKNOWN
    ) {
        val peerName = store.value.progress?.peerName ?: deviceSession.activeDeviceIdValue ?: "Peer"
        store.fail(
            FileTransferFailure(
                peerName = peerName,
                message = message,
                failedFileName = failedFileName,
                direction = direction,
                reason = reason
            )
        )
    }

    private fun resetIncomingTimeout() {
        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = scope.launch {
            delay(SyncProtocolLimits.FILE_IDLE_TIMEOUT_MILLIS.milliseconds)
            incoming.clear()
            fail(
                message = TransferFailureReason.TIMED_OUT.defaultMessage(),
                direction = TransferDirection.RECEIVING,
                reason = TransferFailureReason.TIMED_OUT
            )
            stopService()
            incomingTimeoutJob = null
        }
    }

    private fun startService() = platformOperations.startTransferService()

    private fun stopService() = platformOperations.stopService()
}

private fun FileSendFailure.toFailureReason(): TransferFailureReason = when (this) {
    FileSendFailure.SOURCE_UNAVAILABLE -> TransferFailureReason.SOURCE_UNAVAILABLE
    FileSendFailure.REMOTE_STORAGE_FULL -> TransferFailureReason.STORAGE_FULL
    FileSendFailure.REMOTE_STORAGE_UNAVAILABLE -> TransferFailureReason.STORAGE_UNAVAILABLE
    FileSendFailure.INTEGRITY_FAILED -> TransferFailureReason.INTEGRITY_FAILED
    FileSendFailure.NETWORK_FAILED -> TransferFailureReason.NETWORK_FAILED
}

private fun IncomingUploadFailure?.toFailureReason(): TransferFailureReason = when (this) {
    IncomingUploadFailure.STORAGE_FULL -> TransferFailureReason.STORAGE_FULL
    IncomingUploadFailure.STORAGE_UNAVAILABLE -> TransferFailureReason.STORAGE_UNAVAILABLE
    IncomingUploadFailure.INTEGRITY -> TransferFailureReason.INTEGRITY_FAILED
    IncomingUploadFailure.WRITE_FAILED -> TransferFailureReason.WRITE_FAILED
    IncomingUploadFailure.INVALID_REQUEST -> TransferFailureReason.UNKNOWN
    null -> TransferFailureReason.UNKNOWN
}

private fun TransferFailureReason.defaultMessage(): String = when (this) {
    TransferFailureReason.SOURCE_UNAVAILABLE -> "The selected file could not be read"
    TransferFailureReason.STORAGE_FULL -> "The receiving device does not have enough storage"
    TransferFailureReason.STORAGE_UNAVAILABLE -> "The receiving device cannot access storage"
    TransferFailureReason.INTEGRITY_FAILED -> "File integrity verification failed"
    TransferFailureReason.NETWORK_FAILED -> "The file transfer was interrupted by the network"
    TransferFailureReason.TIMED_OUT -> "The file transfer timed out"
    TransferFailureReason.WRITE_FAILED -> "The received file could not be saved"
    TransferFailureReason.INVALID_SELECTION -> "The selected files are invalid"
    TransferFailureReason.UNKNOWN -> "File transfer failed"
}
