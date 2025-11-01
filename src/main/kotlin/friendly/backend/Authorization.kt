package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException

data class Authorization(val userId: UserId, val token: Token)

val provideAuthorizationMessage = """
Provide Authorization using 'X-User-Id' for userId and 'X-Token' for token
""".trimIndent()

fun RoutingCall.authorizationOrThrow(): Authorization {
    val userId = request.headers["X-User-Id"]
        ?.toLongOrNull()
        ?.let(::UserIdSerializable)
        ?.typed()
        ?: throw SerializationException(provideAuthorizationMessage)
    val token = request.headers["X-Token"]
        ?.let(::TokenSerializable)
        ?.typed()
        ?: throw SerializationException(provideAuthorizationMessage)
    return Authorization(userId, token)
}
