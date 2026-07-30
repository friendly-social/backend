package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException

data class CommunityPostId(val long: Long) {
    fun serializable(): CommunityPostIdSerializable =
        CommunityPostIdSerializable(long)
}

fun CommunityPostId.toCursorId(): CursorId = CursorId(long.toString())

inline fun CursorId.toCommunityPostId(
    fallback: () -> Nothing,
): CommunityPostId? = CommunityPostId(string.toLongOrNull() ?: fallback())

fun RoutingCall.postId(name: String): CommunityPostId {
    val string = parameters[name]
        ?: throw SerializationException(
            "Post id is not optional",
        )
    val long = string.toLongOrNull()
        ?: throw SerializationException(
            "Post id must be an integer",
        )
    return CommunityPostId(long)
}
