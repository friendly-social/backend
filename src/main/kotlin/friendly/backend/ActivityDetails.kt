package friendly.backend

import kotlin.time.Instant

sealed interface ActivityDetails {
    val id: ActivityId
    val instant: Instant
    val isRead: Boolean

    fun serializable(): ActivityDetailsSerializable

    data class NewReply(
        override val id: ActivityId,
        override val instant: Instant,
        override val isRead: Boolean,
        val post: CommunityPostDetails.Plain,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.NewReply(
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

    data class NewRequest(
        override val id: ActivityId,
        override val instant: Instant,
        override val isRead: Boolean,
        val from: UserDetails,
        val isMutual: Boolean,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.NewRequest(
                id = id.serializable(),
                instant = instant,
                isRead = isRead,
                from = from.serializable(),
                isMutual = isMutual,
            )
    }
}
