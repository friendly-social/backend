package friendly.backend.communitypostentity

import friendly.backend.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommunityPostEntitySerializable {
    fun typed(): CommunityPostEntityDetails

    @SerialName("mention")
    @Serializable
    data class Mention(
        val target: String,
        val position: Int,
        val length: Int,
    ) : CommunityPostEntitySerializable {
        override fun typed(): CommunityPostEntityMentionDetails =
            CommunityPostEntityMentionDetails(
                target = UserId(target.toLong()),
                position = position,
                length = length,
            )
    }
}
