package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class FriendshipSerializable(val string: String) {
    init {
        typedOr { string ->
            throw SerializationException("Unknown friendship status '$string'")
        }
    }

    inline fun typedOr(block: (String) -> Friendship): Friendship =
        when (string) {
            "friends" -> Friends
            "incomingRequest" -> IncomingRequest
            "outgoingRequest" -> OutgoingRequest
            "outgoingDecline" -> OutgoingDecline
            "none" -> None
            else -> block(string)
        }

    fun typed(): Friendship = typedOr { string ->
        error("Unknwon friendship status '$string'")
    }
}
