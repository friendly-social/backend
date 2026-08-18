package friendly.backend

import kotlin.time.Instant

sealed interface ActivityDetails {
    val id: ActivityId
    val instant: Instant

    fun serializable(): ActivityDetailsSerializable

    data class Reply(
        override val id: ActivityId,
        override val instant: Instant,
        val post: CommunityPostDetails,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.Reply(
                id = id.serializable(),
                instant = instant,
                post = post.serializable(),
            )
    }
}
