package friendly.backend

sealed interface ActivityEntry {
    val id: ActivityId
    val toId: UserId

    data class Reply(
        override val id: ActivityId,
        override val toId: UserId,
        val postId: CommunityPostId,
    ) : ActivityEntry
}
