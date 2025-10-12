package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class NicknameSerializable(val string: String) {
    init {
        if (string.length > Nickname.MaxLength) {
            throw SerializationException(
                "Nickname length is ${string.length}, but should be less than ${Nickname.MaxLength}",
            )
        }
    }

    fun typed(): Nickname = Nickname.orThrow(string)
}
