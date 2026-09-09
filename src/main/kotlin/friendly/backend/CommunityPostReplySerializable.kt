package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommunityPostReplySerializable {
    @SerialName("single")
    @Serializable
    data class Single(val post: CommunityPostDetailsSerializable) :
        CommunityPostReplySerializable

    @SerialName("thread")
    @Serializable
    data class Thread(val thread: List<CommunityPostDetailsSerializable>) :
        CommunityPostReplySerializable
}
