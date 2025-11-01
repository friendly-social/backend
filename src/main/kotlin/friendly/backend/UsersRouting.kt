package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import kotlinx.serialization.SerializationException

object UsersRouting {
    fun impureDetails(context: AppContext) {
        context.routing.get("/users/details/{id?}/{accessHash?}") {
            val authorization = call.authorizationOrThrow()
            val descriptor = call.descriptorOrThrow()
            val result = UsersService.impureDetails(
                context = context,
                authorization = authorization,
                descriptor = descriptor,
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(result.details.serializable())
            }
        }
    }
}

private fun RoutingCall.descriptorOrThrow(): UsersService.DetailsDescriptor {
    val id = parameters["id"]
        ?.toLong()
        ?.let(::UserIdSerializable)
        ?.typed()
    val accessHash = parameters["accessHash"]
        ?.let(::UserAccessHashSerializable)
        ?.typed()
    return when {
        id == null && accessHash == null ->
            UsersService.DetailsDescriptor.Self
        id != null && accessHash != null ->
            UsersService.DetailsDescriptor.Other(id, accessHash)
        else -> throw SerializationException(
            "Either omit 'id' and 'accessHash' or include both of them",
        )
    }
}
