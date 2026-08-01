package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException

data class UserId(val long: Long) {
    fun serializable(): UserIdSerializable = UserIdSerializable(long)
}

fun RoutingCall.userId(name: String): UserId {
    val string = parameters[name]
        ?: throw SerializationException(
            "$name is not optional",
        )
    val long = string.toLongOrNull()
        ?: throw SerializationException(
            "$name must be an integer",
        )
    return UserId(long)
}
