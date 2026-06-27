package com.liftley.sync360.data.remote

import com.liftley.sync360.domain.model.RequestType
import com.liftley.sync360.domain.model.ServerState
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.domain.remote.response.PingRequestResponse
import com.liftley.sync360.domain.remote.response.PingResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

class Sync360HttpServer(
    private val deviceUuid: String,
    private val incomingServerRequestsController: IncomingServerRequestsController
) {
    private var server: EmbeddedServer<*,*>? = null

    suspend fun start(): Int {
        if (server != null) {
            return server!!.engine.resolvedConnectors().first().port
        }

        val newServer = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                get("/sync360/ping") {
                    try {
                        incomingServerRequestsController.changeServerState(
                            ServerState.Busy(
                                requestType = RequestType.Ping
                            )
                        )
                        val userDecision = incomingServerRequestsController.waitForUserDecision()

                        if (userDecision == UserDecision.ACCEPTED) {
                            call.respond<PingRequestResponse>(
                                PingRequestResponse.Accepted(
                                    PingResponse(
                                        deviceUuid = deviceUuid,
                                        protocolVersion = "1"
                                    )
                                )
                            )
                        } else {
                            call.respond<PingRequestResponse>(
                                PingRequestResponse.Declined("User Declined Your Request")
                            )
                        }
                    } catch (e: Exception){
                        call.respond(PingRequestResponse.Declined(e.message ?: "Cant Perform Operation"))
                    }
                }
            }
        }.start(false)

        server = newServer

        return newServer.engine.resolvedConnectors().first().port
    }
}