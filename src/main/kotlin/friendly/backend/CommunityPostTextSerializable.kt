package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class CommunityPostTextSerializable(val string: String) {
    init {
        if (string.length > CommunityPostText.MaxLength) {
            throw SerializationException(
                "Post cannot be longer than ${CommunityPostText.MaxLength}",
            )
        }
    }

    fun typed(): CommunityPostText = CommunityPostText.orThrow(string)
}
