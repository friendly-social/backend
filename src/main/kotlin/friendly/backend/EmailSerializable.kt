package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class EmailSerializable(val string: String) {
    init {
        if ("@" !in string) {
            throw SerializationException("Email should contain @ symbol")
        }
        if ("." !in string) {
            throw SerializationException("Email should contain . symbol")
        }
        if (string.length > Email.MaxLength) {
            throw SerializationException(
                "Email should not be more than 2048 symbols",
            )
        }
    }

    fun typed(): Email = Email.orThrow(string)
}
