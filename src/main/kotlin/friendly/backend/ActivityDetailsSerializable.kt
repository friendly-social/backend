package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface ActivityDetailsSerializable {
    val id: ActivityIdSerializable
    val instant: Instant

    fun typed(): ActivityDetails

    @Serializable
    @SerialName("reply")
    data class Reply(
        override val id: ActivityIdSerializable,
        override val instant: Instant,
        val post: CommunityPostDetailsSerializable.Plain,
    ) : ActivityDetailsSerializable {
        override fun typed(): ActivityDetails = ActivityDetails.Reply(
            id = id.typed(),
            instant = instant,
            post = post.typed(),
        )
    }
}
