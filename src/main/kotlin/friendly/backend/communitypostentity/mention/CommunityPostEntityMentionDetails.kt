package friendly.backend.communitypostentity

import friendly.backend.UserId

data class CommunityPostEntityMentionDetails(
    val target: UserId,
    val position: Int,
    val length: Int,
) : CommunityPostEntityDetails {
    override fun serializable(): CommunityPostEntitySerializable.Mention =
        CommunityPostEntitySerializable.Mention(
            target = target.long.toString(),
            position = position,
            length = length,
        )
}
