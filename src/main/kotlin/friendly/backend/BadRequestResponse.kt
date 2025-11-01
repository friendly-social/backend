package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.Serializable

@Serializable
data class BadRequestResponse(val message: String)

suspend fun RoutingCall.respondBadRequest(message: String) {
    respond(HttpStatusCode.BadRequest, message)
}
