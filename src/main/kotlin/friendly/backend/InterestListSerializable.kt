package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@JvmInline
@Serializable
value class InterestListSerializable(val raw: List<InterestSerializable>) {
    init {
        if (raw.toSet().size != raw.size) {
            throw SerializationException("A list of interests must be unique")
        }
    }

    fun typed(): InterestList = InterestList.orThrow(
        raw = raw.map(InterestSerializable::typed),
    )
}
