package friendly.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@JvmInline
@Serializable
value class FilePreuploadAccessHashSerializable(val string: String) {
    init {
        if (string.length != FilePreuploadAccessHash.Length) {
            throw SerializationException(
                "FilePreuploadAccessHash is supposed to have a length of ${FilePreuploadAccessHash.Length}, but was ${string.length}",
            )
        }
    }

    fun typed(): FilePreuploadAccessHash =
        FilePreuploadAccessHash.orThrow(string)
}
