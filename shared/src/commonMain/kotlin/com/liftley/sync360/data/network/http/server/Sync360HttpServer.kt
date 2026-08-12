package com.liftley.sync360.data.network.http.server

import com.liftley.sync360.data.IncomingServerRequestsController
import com.liftley.sync360.data.network.http.dto.CancelRequest
import com.liftley.sync360.data.network.http.dto.CancelResponse
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
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
                    val request = call.receive<TextOfferRequest>()
                    val userDecision = incomingServerRequestsController.awaitTextOfferDecision(request)

                    if (userDecision == null) {
                        call.respond(TextOfferResponse.Declined)
                        return@post
                    }

                    call.respond(
                        if (userDecision == UserDecision.ACCEPTED) {
                            TextOfferResponse.Accepted
                        } else {
                            TextOfferResponse.Declined
                        }
                    )
                }

                post("/sync360/text/transfer") {
                    val request = call.receive<TextTransferRequest>()
                    val accepted = incomingServerRequestsController.receiveAcceptedText(
                        operationId = request.operationId,
                        senderDeviceId = request.senderDeviceId,
                        text = request.text
                    )

                    call.respond(
                        TextTransferResponse(
                            success = accepted,
                            message = if (accepted) {
                                null
                            } else {
                                "No matching accepted text offer"
                            }
                        )
                    )
                }

                post("/sync360/file/offer") {
                    val request = call.receive<FileOfferRequest>()
                    val userDecision =
                        incomingServerRequestsController.awaitFileOfferDecision(request)

                    if (userDecision == null) {
                        call.respond(FileOfferResponse.Declined)
                        return@post
                    }

                    if (userDecision != UserDecision.ACCEPTED) {
                        call.respond(FileOfferResponse.Declined)
                        return@post
                    }

                    val prepared = incomingServerRequestsController.prepareAcceptedFileTransfer(
                        operationId = request.operationId
                    ) {
                        fileTransferReceiver.prepareForTransfer(
                            fileOffer = request,
                            onFileSaved = { completedFileCount ->
                                incomingServerRequestsController.updateCompletedFileCount(
                                    operationId = request.operationId,
                                    completedFileCount = completedFileCount
                                )
                            },
                            onProgress = { progress ->
                                incomingServerRequestsController.updateFileProgress(
                                    operationId = request.operationId,
                                    progress = progress
                                )
                            },
                            onTransferFinished = { wasSuccessful ->
                                incomingServerRequestsController.finishFileTransfer(
                                    operationId = request.operationId,
                                    wasSuccessful = wasSuccessful
                                )
                            }
                        )
                    }

                    call.respond(
                        if (prepared) {
                            FileOfferResponse.Accepted
                        } else {
                            FileOfferResponse.Declined
                        }
                    )
                }

                post("/sync360/operation/cancel") {
                    val request = call.receive<CancelRequest>()
                    val cancelled = incomingServerRequestsController.cancelOperation(
                        operationId = request.operationId,
                        senderDeviceId = request.senderDeviceId
                    )

                    if (cancelled) {
                        fileTransferReceiver.cancelCurrentTransfer(request.operationId)
                    }

                    call.respond(
                        CancelResponse(cancelled = cancelled)
                    )
                }
            }
        }.start(false)

        server = newServer
        return newServer.engine.resolvedConnectors().first().port
    }
}
