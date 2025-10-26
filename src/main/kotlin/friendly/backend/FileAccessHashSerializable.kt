package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@JvmInline
@Serializable
value class FileAccessHashSerializable(val string: String) {
    init {
        if (string.length != FileAccessHash.Length) {
            throw SerializationException(
                "FileAccessHash is supposed to have a length of ${FileAccessHash.Length}, but was ${string.length}",
            )
        }
    }

    fun typed(): FileAccessHash = FileAccessHash.orThrow(string)
}
