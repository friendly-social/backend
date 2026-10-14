package friendly.backend

import kotlin.time.Instant

sealed interface CommunityPostDetails {
    val id: CommunityPostId
    val accessHash: CommunityPostAccessHash
    val instant: Instant
    val replyPreviews: List<UserDetails>
    val newReply: NewReply?

    fun serializable(): CommunityPostDetailsSerializable

    data class Plain(
        override val id: CommunityPostId,
        override val accessHash: CommunityPostAccessHash,
        override val instant: Instant,
        override val replyPreviews: List<UserDetails>,
        override val newReply: NewReply?,
        val text: CommunityPostText,
        val owner: UserDetails,
        val edited: Boolean,
    ) : CommunityPostDetails {
        override fun serializable(): CommunityPostDetailsSerializable.Plain =
            CommunityPostDetailsSerializable.Plain(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.serializable()
                },
                newReply = newReply?.serializable(),
                text = text.serializable(),
                owner = owner.serializable(),
                edited = edited,
            )
    }

    data class Deleted(
        override val id: CommunityPostId,
        override val accessHash: CommunityPostAccessHash,
        override val instant: Instant,
        override val replyPreviews: List<UserDetails>,
        override val newReply: NewReply?,
    ) : CommunityPostDetails {
        override fun serializable(): CommunityPostDetailsSerializable.Deleted =
            CommunityPostDetailsSerializable.Deleted(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.serializable()
                },
                newReply = newReply?.serializable(),
            )
    }

    data class NewReply(val id: CommunityPostId, val transitive: Boolean) {
        fun serializable(): CommunityPostDetailsSerializable.NewReply =
            CommunityPostDetailsSerializable.NewReply(
                id = id.serializable(),
                transitive = transitive,
            )
    }
}
