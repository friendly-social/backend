package friendly.backend

sealed interface NotificationPayload {
    val toId: UserId

    data class NewRequest(
        override val toId: UserId,
        val fromId: UserId,
        val isMutual: Boolean,
    ) : NotificationPayload

    data class NewReply(
        override val toId: UserId,
        val postId: CommunityPostId,
    ) : NotificationPayload
}
