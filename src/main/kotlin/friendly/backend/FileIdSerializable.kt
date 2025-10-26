package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class FileIdSerializable(val long: Long) {
    fun typed(): FileId = FileId(long)
}
