package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface CommunityPostDetailsSerializable {
    val id: CommunityPostIdSerializable
    val accessHash: CommunityPostAccessHashSerializable
    val instant: Instant

    fun typed(): CommunityPostDetails

    @SerialName("plain")
    @Serializable
    data class Plain(
        override val id: CommunityPostIdSerializable,
        override val accessHash: CommunityPostAccessHashSerializable,
        override val instant: Instant,
        val text: CommunityPostTextSerializable,
        val owner: UserDetailsSerializable,
        val edited: Boolean,
    ) : CommunityPostDetailsSerializable {
        override fun typed(): CommunityPostDetails.Plain =
            CommunityPostDetails.Plain(
                id = id.typed(),
                accessHash = accessHash.typed(),
                text = text.typed(),
                owner = owner.typed(),
                instant = instant,
                edited = edited,
            )
    }

    @SerialName("deleted")
    @Serializable
    data class Deleted(
        override val id: CommunityPostIdSerializable,
        override val accessHash: CommunityPostAccessHashSerializable,
        override val instant: Instant,
    ) : CommunityPostDetailsSerializable {
        override fun typed(): CommunityPostDetails.Deleted =
            CommunityPostDetails.Deleted(
                id = id.typed(),
                accessHash = accessHash.typed(),
                instant = instant,
            )
    }
}
