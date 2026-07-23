package com.liftley.sync360.data.network.http.server

import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.data.IncomingServerRequestsController
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferResponse
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.domain.model.ClientServerState
import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.UserDecision
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class Sync360HttpServer(
    private val incomingServerRequestsController: IncomingServerRequestsController,
    private val fileTransferReceiver: FileTransferReceiver
) {
    private var server: EmbeddedServer<*, *>? = null

    suspend fun start(): Int {
        if (server != null) {
            return server!!.engine.resolvedConnectors().first().port
        }

        val newServer = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                post("/sync360/text/offer") {
                    val currentState = incomingServerRequestsController.clientServerState.value

                    if (currentState != ClientServerState.Idle) {
                        call.respond(TextOfferResponse.Declined)
                        return@post
                    }
                    val textOfferRequest = call.receive<TextOfferRequest>()

                    incomingServerRequestsController.changeServerState(
                        ClientServerState.TextOffer(
                            senderDeviceName = textOfferRequest.senderDeviceName,
                            preview = textOfferRequest.preview,
                            characterCount = textOfferRequest.characterCount,
                            senderDeviceId = textOfferRequest.senderDeviceId
                        )
                    )

                    val userDecision = withTimeoutOrNull(55_000.milliseconds) {
                        incomingServerRequestsController.waitForUserDecision()
                    }

                    when (userDecision) {
                        UserDecision.ACCEPTED -> {
                            call.respond(TextOfferResponse.Accepted)
                        }

                        UserDecision.DECLINED -> {
                            incomingServerRequestsController.changeServerState(ClientServerState.Idle)
                            call.respond(TextOfferResponse.Declined)
                        }

                        null -> {
                            incomingServerRequestsController.changeServerState(ClientServerState.Idle)
                            call.respond(TextOfferResponse.Declined)
                        }
                    }
                }

                post("/sync360/text/transfer") {
                    try {
                        val textTransferRequest = call.receive<TextTransferRequest>()

                        incomingServerRequestsController.changeServerState(
                            ClientServerState.ReceivedText(
                                textTransferRequest.text
                            )
                        )

                        call.respond(TextTransferResponse(success = true))
                    } catch (e: Exception) {
                        call.respond(
                            TextTransferResponse(
                                success = false,
                                message = e.message ?: "Something went wrong! Please try again"
                            )
                        )
                    }
                }

                post("/sync360/file/offer") {
                    val currentState = incomingServerRequestsController.clientServerState.value

                    if (currentState != ClientServerState.Idle) {
                        call.respond(FileOfferResponse.Declined)
                        return@post
                    }

                    val fileOfferRequest = call.receive<FileOfferRequest>()

                    incomingServerRequestsController.changeServerState(
                        ClientServerState.FileOffer(
                            fileOffer = fileOfferRequest
                        )
                    )

                    val userDecision = withTimeoutOrNull(55_000.milliseconds) {
                        incomingServerRequestsController.waitForUserDecision()
                    }

                    when (userDecision) {
                        UserDecision.ACCEPTED -> {
                            fileTransferReceiver.prepareForTransfer(
                                fileOffer = fileOfferRequest,
                                onFileSaved = { completedFileCount ->
                                    val currentProgress =
                                        (incomingServerRequestsController.clientServerState.value as? ClientServerState.ReceivingFiles)?.progress
                                            ?: FileTransferProgress.waiting(
                                                fileOfferRequest.totalSizeBytes
                                            )
                                    incomingServerRequestsController.changeServerState(
                                        ClientServerState.ReceivingFiles(
                                            senderDeviceName = fileOfferRequest.senderDeviceName,
                                            fileCount = fileOfferRequest.files.size,
                                            completedFileCount = completedFileCount,
                                            progress = currentProgress
                                        )
                                    )
                                },
                                onProgress = { progress ->
                                    val currentState =
                                        incomingServerRequestsController.clientServerState.value as? ClientServerState.ReceivingFiles

                                    if (currentState != null) {
                                        incomingServerRequestsController.changeServerState(
                                            currentState.copy(progress = progress)
                                        )
                                    }
                                },
                                onTransferFinished = { wasSuccessful ->
                                    val finishedState = if (wasSuccessful) {
                                        ClientServerState.ReceivedFiles(
                                            senderDeviceName = fileOfferRequest.senderDeviceName,
                                            fileCount = fileOfferRequest.files.size
                                        )
                                    } else {
                                        ClientServerState.Idle
                                    }

                                    incomingServerRequestsController.changeServerState(finishedState)
                                })

                            incomingServerRequestsController.changeServerState(
                                ClientServerState.ReceivingFiles(
                                    senderDeviceName = fileOfferRequest.senderDeviceName,
                                    fileCount = fileOfferRequest.files.size,
                                    completedFileCount = 0,
                                    progress = FileTransferProgress.waiting(
                                        fileOfferRequest.totalSizeBytes
                                    )
                                )
                            )

                            call.respond(FileOfferResponse.Accepted)
                        }

                        else -> {
                            fileTransferReceiver.clearExpectedTransfer()

                            incomingServerRequestsController.changeServerState(
                                ClientServerState.Idle
                            )

                            call.respond(FileOfferResponse.Declined)
                        }
                    }
                }
            }
        }.start(false)

        server = newServer

        return newServer.engine.resolvedConnectors().first().port
    }
}
