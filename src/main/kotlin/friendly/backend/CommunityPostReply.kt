package friendly.backend

sealed interface CommunityPostReply {
    fun serializable(): CommunityPostReplySerializable

    data class Single(val post: CommunityPostDetails) :
        CommunityPostReply {
        override fun serializable(): CommunityPostReplySerializable.Single =
            CommunityPostReplySerializable.Single(
                post = post.serializable(),
            )
    }

    data class Thread(val thread: List<CommunityPostDetails>) :
        CommunityPostReply {
        override fun serializable(): CommunityPostReplySerializable.Thread =
            CommunityPostReplySerializable.Thread(
                thread = thread.map { post -> post.serializable() },
            )
    }
}
