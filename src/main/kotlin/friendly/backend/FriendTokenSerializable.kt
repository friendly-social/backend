package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class FriendTokenSerializable(val string: String) {
    init {
        if (string.length != FriendToken.Length) {
            throw SerializationException(
                "FriendToken is supposed to have a length of ${FriendToken.Length}, but was ${string.length}",
            )
        }
    }

    fun typed(): FriendToken = FriendToken.orThrow(string)
}
