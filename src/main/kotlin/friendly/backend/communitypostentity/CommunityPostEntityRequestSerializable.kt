package friendly.backend.communitypostentity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommunityPostEntityRequestSerializable {
    fun typed(): CommunityPostEntityRequest

    @SerialName("mention")
    @Serializable
    data class Mention(
        val target: String,
        val position: Int,
        val length: Int,
    ) : CommunityPostEntityRequestSerializable {
        override fun typed(): CommunityPostEntityMentionRequest =
            CommunityPostEntityMentionRequest(
                target = target,
                position = position,
                length = length,
            )
    }
}
