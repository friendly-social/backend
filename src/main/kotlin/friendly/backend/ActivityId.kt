package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException

data class ActivityId(val long: Long) {
    fun serializable(): ActivityIdSerializable = ActivityIdSerializable(long)
}

inline fun CursorId.toActivityId(fallback: () -> Nothing): ActivityId? =
    ActivityId(string.toLongOrNull() ?: fallback())

fun RoutingCall.activityId(name: String): ActivityId {
    val string = parameters[name]
        ?: throw SerializationException(
            "$name is not optional",
        )
    val long = string.toLongOrNull()
        ?: throw SerializationException(
            "$name must be an integer",
        )
    return ActivityId(long)
}
