package friendly.backend

data class CommunityPostId(val long: Long) {
    fun serializable(): CommunityPostIdSerializable =
        CommunityPostIdSerializable(long)
}

fun CommunityPostId.toCursorId(): CursorId = CursorId(long.toString())

inline fun CursorId.toCommunityPostId(
    fallback: () -> Nothing,
): CommunityPostId? = CommunityPostId(string.toLongOrNull() ?: fallback())
