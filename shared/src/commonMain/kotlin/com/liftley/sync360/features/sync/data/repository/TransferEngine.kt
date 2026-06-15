package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.data.network.FileSendFailure
import com.liftley.sync360.features.sync.data.network.FileSendResult
import com.liftley.sync360.features.sync.data.network.IncomingUploadFailure
import com.liftley.sync360.features.sync.data.network.OutgoingFileTransferCoordinator
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.domain.diagnostics.TransferDiagnostics
import com.liftley.sync360.features.sync.domain.diagnostics.transferExecutionContext
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
internal class TransferEngine(
    private val scope: CoroutineScope,
    private val incomingNotifier: IncomingMessageNotifier,
    private val platformOperations: PlatformOperations,
    private val deviceSession: DeviceSessionStore,
    private val deviceRegistry: DeviceRegistry,
    private val incoming: IncomingFileTransferCoordinator,
    private val outgoing: OutgoingFileTransferCoordinator,
    private val onIncomingTerminated: () -> Unit
) {
    private val store = TransferStore()
    private val incomingActivityLock = Any()
    private var outgoingJob: Job? = null
    private var incomingTimeoutJob: Job? = null
    private var lastIncomingActivityMillis = 0L
    private var progressDiagnosticStartedAt: TimeMark? = null
    private var progressDiagnosticBytes = 0L
    private var progressDiagnosticDirection: TransferDirection? = null
    private var progressUpdateAttempts = 0L
    private var progressStateUpdates = 0L
    private var stageUpdateAttempts = 0L
    private var stageStateUpdates = 0L
    private var progressUpdateNanos = 0L
    private var progressExecutionContext = ""

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
            startProgressDiagnostics(TransferDirection.SENDING, totalBytes)
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
                store.succeed(TransferDirection.SENDING)
                finishProgressDiagnostics("success")
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
        markIncomingActivity()
        startProgressDiagnostics(
            TransferDirection.RECEIVING,
            start.progress.files.sumOf { it.sizeBytes }
        )
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

        stopIncomingWatchdog()
        updateProgress(100)
        store.succeed(TransferDirection.RECEIVING)
        finishProgressDiagnostics("success")
        stopService()
        return true
    }

    fun initIncomingRawFile(
        offerId: String,
        fileIndex: Int,
        declaredLength: Long,
        fileIdentifier: String,
        remoteHost: String
    ): Boolean {
        if (deviceRegistry.approvedSessions.value.none { it.route.host == remoteHost }) return false
        val accepted = incoming.initRawFileWrite(
            offerId,
            fileIndex,
            declaredLength,
            fileIdentifier
        )
        if (accepted) markIncomingActivity()
        return accepted
    }

    fun receiveChunk(offerId: String, fileIndex: Int, chunk: ByteArray, offset: Int, length: Int): Boolean {
        updateStage(TransferStage.TRANSFERRING)
        val written = incoming.writeChunk(offerId, fileIndex, chunk, offset, length)
        if (written) markIncomingActivity()
        return written
    }

    fun completeIncomingFile(offerId: String, fileIndex: Int): String? {
        updateStage(TransferStage.VERIFYING)
        updateProgress(99)
        val complete = incoming.completeFileWrite(offerId, fileIndex)
        if (complete.batch != null) {
            stopIncomingWatchdog()
            store.succeed(TransferDirection.RECEIVING, complete.batch)
            finishProgressDiagnostics("success")
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
        stopIncomingWatchdog()
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
        stopIncomingWatchdog()
        incoming.clear()
        store.clear()
        finishProgressDiagnostics("cancelled")
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
        val updateStarted = TimeSource.Monotonic.markNow()
        if (progressDiagnosticStartedAt != null) {
            progressUpdateAttempts += 1
            progressExecutionContext = transferExecutionContext()
        }
        val current = store.value.progress
        if (current != null && current.percent != percent) {
            store.updateProgress(percent)
            if (progressDiagnosticStartedAt != null) progressStateUpdates += 1
        }
        if (progressDiagnosticStartedAt != null) {
            progressUpdateNanos += updateStarted.elapsedNow().inWholeNanoseconds
        }
    }

    private fun updateStage(stage: TransferStage) {
        val updateStarted = TimeSource.Monotonic.markNow()
        if (progressDiagnosticStartedAt != null) {
            stageUpdateAttempts += 1
            progressExecutionContext = transferExecutionContext()
        }
        val current = store.value.progress
        if (current != null && current.stage != stage) {
            store.updateStage(stage)
            if (progressDiagnosticStartedAt != null) stageStateUpdates += 1
        }
        if (progressDiagnosticStartedAt != null) {
            progressUpdateNanos += updateStarted.elapsedNow().inWholeNanoseconds
        }
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
        finishProgressDiagnostics("failure_${reason.name}")
    }

    private fun markIncomingActivity() {
        synchronized(incomingActivityLock) {
            lastIncomingActivityMillis = Clock.System.now().toEpochMilliseconds()
        }
        if (incomingTimeoutJob?.isActive == true) return

        incomingTimeoutJob = scope.launch {
            while (true) {
                val idleMillis = synchronized(incomingActivityLock) {
                    Clock.System.now().toEpochMilliseconds() - lastIncomingActivityMillis
                }
                val remainingMillis = SyncProtocolLimits.FILE_IDLE_TIMEOUT_MILLIS - idleMillis
                if (remainingMillis <= 0L) break
                delay(remainingMillis.milliseconds)
            }
            incoming.clear()
            fail(
                message = TransferFailureReason.TIMED_OUT.defaultMessage(),
                direction = TransferDirection.RECEIVING,
                reason = TransferFailureReason.TIMED_OUT
            )
            stopService()
            onIncomingTerminated()
            incomingTimeoutJob = null
        }
    }

    private fun stopIncomingWatchdog() {
        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = null
        synchronized(incomingActivityLock) {
            lastIncomingActivityMillis = 0L
        }
    }

    private fun startProgressDiagnostics(direction: TransferDirection, totalBytes: Long) {
        progressDiagnosticStartedAt = TimeSource.Monotonic.markNow()
        progressDiagnosticBytes = totalBytes
        progressDiagnosticDirection = direction
        progressUpdateAttempts = 0L
        progressStateUpdates = 0L
        stageUpdateAttempts = 0L
        stageStateUpdates = 0L
        progressUpdateNanos = 0L
        progressExecutionContext = transferExecutionContext()
    }

    private fun finishProgressDiagnostics(outcome: String) {
        val startedAt = progressDiagnosticStartedAt ?: return
        val direction = progressDiagnosticDirection ?: return
        TransferDiagnostics.log(
            stage = "ui_progress_updates",
            bytes = progressDiagnosticBytes,
            elapsedNanos = progressUpdateNanos,
            bufferBytes = TRANSFER_BUFFER_BYTES,
            dispatcher = "Caller coroutine; sender Default, receiver Ktor CIO",
            streamed = true,
            fullFileInMemory = false,
            base64 = false,
            stringEncoding = false,
            json = false,
            multipart = false,
            executionContext = progressExecutionContext,
            details = "direction=${direction.name}" +
                " attempts=$progressUpdateAttempts stateUpdates=$progressStateUpdates" +
                " stageAttempts=$stageUpdateAttempts stageUpdates=$stageStateUpdates" +
                " transferWallMs=${startedAt.elapsedNow().inWholeMilliseconds}" +
                " outcome=$outcome"
        )
        progressDiagnosticStartedAt = null
        progressDiagnosticBytes = 0L
        progressDiagnosticDirection = null
    }

    private fun startService() = platformOperations.startTransferService()

    private fun stopService() = platformOperations.stopService()

    private companion object {
        const val TRANSFER_BUFFER_BYTES = 1024 * 1024
    }
}

private fun FileSendFailure.toFailureReason(): TransferFailureReason = when (this) {
    FileSendFailure.SOURCE_UNAVAILABLE -> TransferFailureReason.SOURCE_UNAVAILABLE
    FileSendFailure.REMOTE_STORAGE_FULL -> TransferFailureReason.STORAGE_FULL
    FileSendFailure.REMOTE_STORAGE_UNAVAILABLE -> TransferFailureReason.STORAGE_UNAVAILABLE
    FileSendFailure.INTEGRITY_FAILED -> TransferFailureReason.INTEGRITY_FAILED
    FileSendFailure.TIMED_OUT -> TransferFailureReason.TIMED_OUT
    FileSendFailure.RECEIVER_UNAVAILABLE -> TransferFailureReason.RECEIVER_UNAVAILABLE
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
    TransferFailureReason.RECEIVER_UNAVAILABLE -> "The receiving device is unavailable"
    TransferFailureReason.NETWORK_FAILED -> "The file transfer was interrupted by the network"
    TransferFailureReason.TIMED_OUT -> "The file transfer timed out"
    TransferFailureReason.WRITE_FAILED -> "The received file could not be saved"
    TransferFailureReason.INVALID_SELECTION -> "The selected files are invalid"
    TransferFailureReason.UNKNOWN -> "File transfer failed"
}
