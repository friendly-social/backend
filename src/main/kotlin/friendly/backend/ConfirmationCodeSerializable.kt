package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class ConfirmationCodeSerializable(val int: Int) {
    init {
        if (int !in ConfirmationCode.Min..ConfirmationCode.Max) {
            throw SerializationException("Codes are exactly 8 digits long")
        }
    }

    fun typed(): ConfirmationCode = ConfirmationCode.orThrow(int)
}
