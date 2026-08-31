package friendly.backend

import kotlin.time.Instant

sealed interface ActivityDetails {
    val id: ActivityId
    val instant: Instant
    val isRead: Boolean

    fun serializable(): ActivityDetailsSerializable

    data class Reply(
        override val id: ActivityId,
        override val instant: Instant,
        override val isRead: Boolean,
        val post: CommunityPostDetails.Plain,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.Reply(
                id = id.serializable(),
                instant = instant,
                isRead = isRead,
                post = post.serializable(),
            )
    }
}
