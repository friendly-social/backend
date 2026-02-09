package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
@JvmInline
value class LoginCodeSerializable(val int: Int) {
    init {
        if (int !in LoginCode.Min..LoginCode.Max) {
            throw SerializationException("Codes are exactly 8 digits long")
        }
    }

    fun typed(): LoginCode = LoginCode.orThrow(int)
}
