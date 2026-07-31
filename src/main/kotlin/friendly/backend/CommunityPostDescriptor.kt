package friendly.backend

data class CommunityPostDescriptor(
    val id: CommunityPostId,
    val accessHash: CommunityPostAccessHash,
) {
    fun serializable(): CommunityPostDescriptorSerializable =
        CommunityPostDescriptorSerializable(
            id = id.serializable(),
            accessHash = accessHash.serializable(),
        )
}
