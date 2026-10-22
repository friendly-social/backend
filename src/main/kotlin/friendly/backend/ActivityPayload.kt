package friendly.backend

import kotlin.time.Instant

sealed interface ActivityPayload {
    val toId: UserId
    val instant: Instant

    data class NewReply(
        override val toId: UserId,
        override val instant: Instant,
        val postId: CommunityPostId,
    ) : ActivityPayload

    data class IndirectReplies(
        override val toId: UserId,
        override val instant: Instant,
        val postId: CommunityPostId,
    ) : ActivityPayload

    data class NewRequest(
        override val toId: UserId,
        override val instant: Instant,
        val fromId: UserId,
        val isMutual: Boolean,
    ) : ActivityPayload
}
