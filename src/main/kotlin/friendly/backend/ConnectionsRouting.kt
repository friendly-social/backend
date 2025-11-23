package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ConnectionsRouting {

    @Serializable
    private data class RequestBody(
        val userId: UserIdSerializable,
        val userAccessHash: UserAccessHashSerializable,
    )

    @Serializable
    data object RequestResponse

    fun impureRequest(context: AppContext) {
        context.routing.post("/connections/request") {
            val authorization = call.authorizationOrThrow()
            val body = call.receive<RequestBody>()
            val result = ConnectionsService.impureRequest(
                context = context,
                authorization = authorization,
                userId = body.userId.typed(),
                userAccessHash = body.userAccessHash.typed(),
            )
            when (result) {
                is Unauthorized -> call.respondAuthorizationInvalid()
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(RequestResponse)
            }
        }
    }

    @Serializable
    private data class DeclineBody(
        val userId: UserIdSerializable,
        val userAccessHash: UserAccessHashSerializable,
    )

    @Serializable
    data object DeclineResponse

    fun impureDecline(context: AppContext) {
        context.routing.post("/connections/decline") {
            val authorization = call.authorizationOrThrow()
            val body = call.receive<DeclineBody>()
            val result = ConnectionsService.impureDecline(
                context = context,
                authorization = authorization,
                userId = body.userId.typed(),
                userAccessHash = body.userAccessHash.typed(),
            )
            when (result) {
                is Unauthorized -> call.respondAuthorizationInvalid()
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(DeclineResponse)
            }
        }
    }
}
