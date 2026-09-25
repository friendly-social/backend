package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class FilePreuploadDescriptorSerializable(
    val id: FilePreuploadIdSerializable,
    val accessHash: FilePreuploadAccessHashSerializable,
) {
    fun typed(): FilePreuploadDescriptor =
        FilePreuploadDescriptor(id.typed(), accessHash.typed())
}
