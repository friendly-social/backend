package friendly.backend.communitypostentity

data class CommunityPostEntityMention(
    override val id: CommunityPostEntityId,
    val target: Long,
    val position: Int,
    val length: Int,
) : CommunityPostEntity
