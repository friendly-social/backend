package friendly.backend

data class FilePreuploadDescriptor(
    val id: FilePreuploadId,
    val accessHash: FilePreuploadAccessHash,
) {
    fun serializable(): FilePreuploadDescriptorSerializable =
        FilePreuploadDescriptorSerializable(
            id = id.serializable(),
            accessHash = accessHash.serializable(),
        )
}
