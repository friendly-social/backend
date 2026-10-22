package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface ActivityDetailsSerializable {
    val id: ActivityIdSerializable
    val instant: Instant
    val isRead: Boolean

    fun typed(): ActivityDetails

    @Serializable
    @SerialName("new_reply")
    data class NewReply(
        override val id: ActivityIdSerializable,
        override val instant: Instant,
        override val isRead: Boolean,
        val post: CommunityPostDetailsSerializable.Plain,
    ) : ActivityDetailsSerializable {
        override fun typed(): ActivityDetails = ActivityDetails.NewReply(
            id = id.typed(),
            instant = instant,
            isRead = isRead,
            post = post.typed(),
        )
    }

    @Serializable
    @SerialName("indirect_replies")
    data class IndirectReplies(
        override val id: ActivityIdSerializable,
        override val instant: Instant,
        override val isRead: Boolean,
        val post: CommunityPostDetailsSerializable.Plain,
        val commonDenominator: CommunityPostDetailsSerializable,
    ) : ActivityDetailsSerializable {
        override fun typed(): ActivityDetails = ActivityDetails.IndirectReplies(
            id = id.typed(),
            instant = instant,
            isRead = isRead,
            post = post.typed(),
            commonDenominator = commonDenominator.typed(),
        )
    }

    @Serializable
    @SerialName("new_request")
    data class NewRequest(
        override val id: ActivityIdSerializable,
        override val instant: Instant,
        override val isRead: Boolean,
        val from: UserDetailsSerializable,
        val isMutual: Boolean,
    ) : ActivityDetailsSerializable {
        override fun typed(): ActivityDetails = ActivityDetails.NewRequest(
            id = id.typed(),
            instant = instant,
            isRead = isRead,
            from = from.typed(),
            isMutual = isMutual,
        )
    }
}
