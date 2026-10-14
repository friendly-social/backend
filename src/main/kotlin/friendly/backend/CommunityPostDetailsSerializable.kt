package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface CommunityPostDetailsSerializable {
    val id: CommunityPostIdSerializable
    val accessHash: CommunityPostAccessHashSerializable
    val instant: Instant
    val replyPreviews: List<UserDetailsSerializable>
    val newReply: NewReply?

    fun typed(): CommunityPostDetails

    @SerialName("plain")
    @Serializable
    data class Plain(
        override val id: CommunityPostIdSerializable,
        override val accessHash: CommunityPostAccessHashSerializable,
        override val instant: Instant,
        override val replyPreviews: List<UserDetailsSerializable>,
        override val newReply: NewReply?,
        val text: CommunityPostTextSerializable,
        val owner: UserDetailsSerializable,
        val edited: Boolean,
    ) : CommunityPostDetailsSerializable {
        override fun typed(): CommunityPostDetails.Plain =
            CommunityPostDetails.Plain(
                id = id.typed(),
                accessHash = accessHash.typed(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.typed()
                },
                newReply = newReply?.typed(),
                text = text.typed(),
                owner = owner.typed(),
                edited = edited,
            )
    }

    @SerialName("deleted")
    @Serializable
    data class Deleted(
        override val id: CommunityPostIdSerializable,
        override val accessHash: CommunityPostAccessHashSerializable,
        override val instant: Instant,
        override val replyPreviews: List<UserDetailsSerializable>,
        override val newReply: NewReply?,
    ) : CommunityPostDetailsSerializable {
        override fun typed(): CommunityPostDetails.Deleted =
            CommunityPostDetails.Deleted(
                id = id.typed(),
                accessHash = accessHash.typed(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.typed()
                },
                newReply = newReply?.typed(),
            )
    }

    @Serializable
    data class NewReply(
        val id: CommunityPostIdSerializable,
        val transitive: Boolean,
    ) {
        fun typed(): CommunityPostDetails.NewReply =
            CommunityPostDetails.NewReply(
                id = id.typed(),
                transitive = transitive,
            )
    }
}
