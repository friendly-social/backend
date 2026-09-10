package friendly.backend.communitypostentity

import friendly.backend.UserAccessHashSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommunityPostEntitySerializable {
    fun typed(): CommunityPostEntityDetails

    @SerialName("mention")
    @Serializable
    data class Mention(
        val target: UserAccessHashSerializable,
        val position: Int,
        val length: Int,
    ) : CommunityPostEntitySerializable {
        override fun typed(): CommunityPostEntityMentionDetails =
            CommunityPostEntityMentionDetails(
                target = target.typed(),
                position = position,
                length = length,
            )
    }
}
