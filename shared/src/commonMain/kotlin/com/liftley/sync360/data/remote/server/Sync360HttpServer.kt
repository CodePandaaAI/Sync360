package com.liftley.sync360.data.remote.server

import com.liftley.sync360.data.remote.IncomingServerRequestsController
import com.liftley.sync360.data.remote.client.clientRequest.MessagePayload
import com.liftley.sync360.data.remote.client.clientRequest.OfferRequest
import com.liftley.sync360.data.remote.server.serverResponse.BaseResponse
import com.liftley.sync360.data.remote.server.serverResponse.OfferResponse
import com.liftley.sync360.domain.model.ClientServerState
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
    private val deviceUuid: String,
    private val incomingServerRequestsController: IncomingServerRequestsController
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
                post("/sync360/offer") {
                    val offerRequest = call.receive<OfferRequest>()
                    incomingServerRequestsController.changeServerState(
                        ClientServerState.Busy(
                            offerRequest
                        )
                    )
                    val userDecision = withTimeoutOrNull(55_000.milliseconds) {
                        incomingServerRequestsController.waitForUserDecision()
                    }

                    if (userDecision == UserDecision.ACCEPTED) {
                        call.respond(OfferResponse.OfferAccepted)
                    } else {
                        incomingServerRequestsController.changeServerState(ClientServerState.Idle)
                        call.respond(OfferResponse.OfferDeclined)
                    }
                }

                post("/sync360/files") {
                    val dataPayload = call.receive<MessagePayload>()

                    incomingServerRequestsController.changeServerState(ClientServerState.Received(dataPayload))

                    call.respond<BaseResponse>(
                        status = io.ktor.http.HttpStatusCode.OK,
                        message = BaseResponse(
                            success = true,
                            message = "Payload received successfully"
                        )
                    )
                }
            }
        }.start(false)

        server = newServer

        return newServer.engine.resolvedConnectors().first().port
    }
}