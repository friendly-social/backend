package friendly.backend

import kotlin.time.Instant

sealed interface ActivityPayload {
    val toId: UserId
    val instant: Instant

    data class Reply(
        override val toId: UserId,
        override val instant: Instant,
        val postId: CommunityPostId,
    ) : ActivityPayload
}
