package friendly.backend

import kotlin.time.Instant

sealed interface ActivityEntry {
    val id: ActivityId
    val toId: UserId
    val instant: Instant
    val isRead: Boolean

    data class Reply(
        override val id: ActivityId,
        override val toId: UserId,
        override val instant: Instant,
        override val isRead: Boolean,
        val postId: CommunityPostId,
    ) : ActivityEntry
}
