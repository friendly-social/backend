package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class FilePreuploadIdSerializable(val long: Long) {
    fun typed(): FilePreuploadId = FilePreuploadId(long)
}
