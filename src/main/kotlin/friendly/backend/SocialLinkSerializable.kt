package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class SocialLinkSerializable(val string: String) {
    init {
        if (string.length > SocialLink.MaxLength) {
            throw SerializationException(
                "Nickname length is ${string.length}, but should be less than ${SocialLink.MaxLength}",
            )
        }
        if (string.isBlank()) {
            throw SerializationException("Social link cannot be blank")
        }
    }

    fun typed(): SocialLink = SocialLink.orThrow(string)
}
