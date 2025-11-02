package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class FileDescriptorSerializable(
    val id: FileIdSerializable,
    val accessHash: FileAccessHashSerializable,
) {
    fun typed(): FileDescriptor = FileDescriptor(id.typed(), accessHash.typed())
}
