package friendly.backend

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CommunityPostSerializable(
    val id: CommunityPostIdSerializable,
    val text: CommunityPostTextSerializable,
    val owner: UserDetailsSerializable,
    val instant: Instant,
) {
    fun typed(): CommunityPost = CommunityPost(
        id = id.typed(),
        text = text.typed(),
        owner = owner.typed(),
        instant = instant,
    )
}
