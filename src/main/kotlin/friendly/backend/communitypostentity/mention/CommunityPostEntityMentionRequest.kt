package friendly.backend.communitypostentity

data class CommunityPostEntityMentionRequest(
    val target: String,
    val position: Int,
    val length: Int,
) : CommunityPostEntityRequest
