package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class CommunityPostAccessHashSerializable(val string: String) {
    init {
        if (string.length != CommunityPostAccessHash.Length) {
            throw SerializationException(
                "CommunityPostAccessHash is supposed to have a length of ${CommunityPostAccessHash.Length}, but was ${string.length}",
            )
        }
    }
    fun typed(): CommunityPostAccessHash =
        CommunityPostAccessHash.orThrow(string)
}
