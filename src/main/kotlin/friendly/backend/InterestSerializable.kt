package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@JvmInline
@Serializable
value class InterestSerializable(val string: String) {
    init {
        if (string.length > Interest.MaxLength) {
            throw SerializationException(
                "Interest length might not be more than ${Interest.MaxLength}, was ${string.length}",
            )
        }
        if (string.isBlank()) {
            throw SerializationException(
                "Empty interests are not allowed",
            )
        }
    }

    fun typed(): Interest = Interest.orThrow(string)
}

fun List<InterestSerializable>.typed(): List<Interest> =
    map(InterestSerializable::typed)
