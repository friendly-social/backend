package friendly.backend

import friendly.backend.communitypostentity.CommunityPostEntityDetails
import kotlin.time.Instant

sealed interface CommunityPostDetails {
    val id: CommunityPostId
    val accessHash: CommunityPostAccessHash
    val instant: Instant
    val replyPreviews: List<UserDetails>

    fun serializable(): CommunityPostDetailsSerializable

    data class Plain(
        override val id: CommunityPostId,
        override val accessHash: CommunityPostAccessHash,
        override val instant: Instant,
        override val replyPreviews: List<UserDetails>,
        val text: CommunityPostText,
        val owner: UserDetails,
        val edited: Boolean,
        val entities: List<CommunityPostEntityDetails>,
    ) : CommunityPostDetails {
        override fun serializable(): CommunityPostDetailsSerializable.Plain =
            CommunityPostDetailsSerializable.Plain(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.serializable()
                },
                text = text.serializable(),
                owner = owner.serializable(),
                edited = edited,
                entities = entities.map { entity -> entity.serializable() },
            )
    }

    data class Deleted(
        override val id: CommunityPostId,
        override val accessHash: CommunityPostAccessHash,
        override val instant: Instant,
        override val replyPreviews: List<UserDetails>,
    ) : CommunityPostDetails {
        override fun serializable(): CommunityPostDetailsSerializable.Deleted =
            CommunityPostDetailsSerializable.Deleted(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                instant = instant,
                replyPreviews = replyPreviews.map { preview ->
                    preview.serializable()
                },
            )
    }
}
