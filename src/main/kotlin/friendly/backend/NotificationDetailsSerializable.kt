package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NotificationDetailsSerializable {
    fun typed(): NotificationDetails

    @Serializable
    @SerialName("new_request")
    data class NewRequest(
        val from: UserDetailsSerializable,
        val isMutual: Boolean,
    ) : NotificationDetailsSerializable {
        override fun typed(): NotificationDetails =
            NotificationDetails.NewRequest(
                from = from.typed(),
                isMutual = isMutual,
            )
    }

    @Serializable
    @SerialName("new_reply")
    data class NewReply(val post: CommunityPostDetailsSerializable) :
        NotificationDetailsSerializable {
        override fun typed(): NotificationDetails =
            NotificationDetails.NewReply(
                post = post.typed(),
            )
    }
}
