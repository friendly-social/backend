package friendly.backend

sealed interface ActivityPayload {
    val toId: UserId

    data class Reply(override val toId: UserId, val postId: CommunityPostId) :
        ActivityPayload
}
