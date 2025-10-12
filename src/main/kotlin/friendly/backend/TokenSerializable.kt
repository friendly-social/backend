package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class TokenSerializable(val string: String) {
    init {
        if (string.length != Token.Length) {
            throw SerializationException(
                "Token is supposed to have a length of ${Token.Length}, but was ${string.length}",
            )
        }
    }

    fun typed(): Token = Token.orThrow(string)
}
