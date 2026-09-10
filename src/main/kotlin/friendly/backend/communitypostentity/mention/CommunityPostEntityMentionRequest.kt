package friendly.backend.communitypostentity

import friendly.backend.UserAccessHash

data class CommunityPostEntityMentionRequest(
    val target: UserAccessHash,
    val position: Int,
    val length: Int,
) : CommunityPostEntityRequest
