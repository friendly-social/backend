package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@JvmInline
@Serializable
value class UserAccessHashSerializable(val string: String) {
    init {
        if (string.length != UserAccessHash.Length) {
            throw SerializationException(
                "UserAccessHash is supposed to have a length of ${UserAccessHash.Length}, but was ${string.length}",
            )
        }
    }

    fun typed(): UserAccessHash = UserAccessHash.orThrow(string)
}
