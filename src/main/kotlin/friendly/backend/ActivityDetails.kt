package friendly.backend

sealed interface ActivityDetails {
    val id: ActivityId

    fun serializable(): ActivityDetailsSerializable

    data class Reply(
        override val id: ActivityId,
        val post: CommunityPostDetails,
    ) : ActivityDetails {
        override fun serializable(): ActivityDetailsSerializable =
            ActivityDetailsSerializable.Reply(
                id = id.serializable(),
                post = post.serializable(),
            )
    }
}
