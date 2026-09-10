package friendly.backend

import friendly.backend.communitypostentity.CommunityPostEntitySerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface CommunityPostDetailsSerializable {
    val id: CommunityPostIdSerializable
    val accessHash: CommunityPostAccessHashSerializable
    val instant: Instant
    val replyPreviews: List<UserDetailsSerializable>

    fun typed(): CommunityPostDetails

    @SerialName("plain")
    @Serializable
    data class Plain(
        override val id: CommunityPostIdSerializable,
        override val accessHash: CommunityPostAccessHashSerializable,
        override val instant: Instant,
        override val replyPreviews: List<UserDetailsSerializable>,
        val text: CommunityPostTextSerializable,
        val owner: UserDetailsSerializable,
        val edited: Boolean,
        val entities: List<CommunityPostEntitySerializable>,
    ) : CommunityPostDetailsSerializable {
        override fun typed(): CommunityPostDetails.Plain =
            CommunityPostDetails.Plain(
                id = id.typed(),
                accessHash = accessHash.typed(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.typed()
                },
                text = text.typed(),
                owner = owner.typed(),
                edited = edited,
                entities = entities.map { entity -> entity.typed() },
            )
    }

    @SerialName("deleted")
    @Serializable
    data class Deleted(
        override val id: CommunityPostIdSerializable,
        override val accessHash: CommunityPostAccessHashSerializable,
        override val instant: Instant,
        override val replyPreviews: List<UserDetailsSerializable>,
    ) : CommunityPostDetailsSerializable {
        override fun typed(): CommunityPostDetails.Deleted =
            CommunityPostDetails.Deleted(
                id = id.typed(),
                accessHash = accessHash.typed(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.typed()
                },
            )
    }
}
