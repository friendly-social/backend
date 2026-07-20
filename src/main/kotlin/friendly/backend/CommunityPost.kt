package friendly.backend

import kotlin.time.Instant

data class CommunityPost(
    val id: CommunityPostId,
    val text: CommunityPostText,
    val owner: UserDetails,
    val instant: Instant,
) {
    fun serializable(): CommunityPostSerializable = CommunityPostSerializable(
        id = id.serializable(),
        text = text.serializable(),
        owner = owner.serializable(),
        instant = instant,
    )
}
