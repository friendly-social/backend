package friendly.backend

data class FileDescriptor(val id: FileId, val accessHash: FileAccessHash) {
    fun serializable(): FileDescriptorSerializable = FileDescriptorSerializable(
        id = id.serializable(),
        accessHash = accessHash.serializable(),
    )
}
