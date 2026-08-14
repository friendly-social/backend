package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ActivityDetailsSerializable {
    val id: ActivityIdSerializable

    fun typed(): ActivityDetails

    @Serializable
    @SerialName("reply")
    data class Reply(
        override val id: ActivityIdSerializable,
        val post: CommunityPostDetailsSerializable,
    ) : ActivityDetailsSerializable {
        override fun typed(): ActivityDetails = ActivityDetails.Reply(
            id = id.typed(),
            post = post.typed(),
        )
    }
}
