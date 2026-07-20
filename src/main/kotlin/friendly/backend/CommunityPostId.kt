package friendly.backend

data class CommunityPostId(val long: Long) {
    fun serializable(): CommunityPostIdSerializable =
        CommunityPostIdSerializable(long)
}
