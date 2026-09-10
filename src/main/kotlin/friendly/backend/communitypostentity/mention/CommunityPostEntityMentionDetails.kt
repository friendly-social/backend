package friendly.backend.communitypostentity

import friendly.backend.UserAccessHash

data class CommunityPostEntityMentionDetails(
    val target: UserAccessHash,
    val position: Int,
    val length: Int,
) : CommunityPostEntityDetails {
    override fun serializable(): CommunityPostEntitySerializable.Mention =
        CommunityPostEntitySerializable.Mention(
            target = target.serializable(),
            position = position,
            length = length,
        )
}
