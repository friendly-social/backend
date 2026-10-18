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

    data class IndirectReplies(
        override val id: ActivityId,
        override val instant: Instant,
        override val isRead: Boolean,
        val post: CommunityPostDetails.Plain,
        val commonDenominator: CommunityPostDetails,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.IndirectReplies(
                id = id.serializable(),
                instant = instant,
                isRead = isRead,
                post = post.serializable(),
                commonDenominator = commonDenominator.serializable(),
            )
    }

    data class FriendRequest(
        override val id: ActivityId,
        override val instant: Instant,
        override val isRead: Boolean,
        val from: UserDetails,
        val isComplete: Boolean,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.FriendRequest(
                id = id.serializable(),
                instant = instant,
                isRead = isRead,
                from = from.serializable(),
                isComplete = isComplete,
            )
    }
}
