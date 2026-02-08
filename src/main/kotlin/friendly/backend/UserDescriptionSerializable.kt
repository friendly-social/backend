package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class UserDescriptionSerializable(val string: String) {
    init {
        if (string.length > UserDescription.MaxLength) {
            throw SerializationException(
                "User description length must not exceed ${UserDescription.MaxLength}, was ${string.length}",
            )
        }
        if (string.isBlank()) {
            throw SerializationException("User description cannot be blank")
        }
    }

    fun typed(): UserDescription = UserDescription.orThrow(string)
}
