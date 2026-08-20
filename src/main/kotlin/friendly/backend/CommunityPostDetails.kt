package friendly.backend

import kotlin.time.Instant

sealed interface CommunityPostDetails {
    val id: CommunityPostId
    val accessHash: CommunityPostAccessHash
    val instant: Instant

    fun serializable(): CommunityPostDetailsSerializable

    data class Plain(
        override val id: CommunityPostId,
        override val accessHash: CommunityPostAccessHash,
        override val instant: Instant,
        val text: CommunityPostText,
        val owner: UserDetails,
        val edited: Boolean,
    ) : CommunityPostDetails {
        override fun serializable(): CommunityPostDetailsSerializable.Plain =
            CommunityPostDetailsSerializable.Plain(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                instant = instant,
                text = text.serializable(),
                owner = owner.serializable(),
                edited = edited,
            )
    }

    data class Deleted(
        override val id: CommunityPostId,
        override val accessHash: CommunityPostAccessHash,
        override val instant: Instant,
    ) : CommunityPostDetails {
        override fun serializable(): CommunityPostDetailsSerializable.Deleted =
            CommunityPostDetailsSerializable.Deleted(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                instant = instant,
            )
    }
}
