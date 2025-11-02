package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class UserDetailsSerializable(
    val id: UserIdSerializable,
    val accessHash: UserAccessHashSerializable,
    val nickname: NicknameSerializable,
    val description: UserDescriptionSerializable,
    val interests: List<InterestSerializable>,
    val avatar: FileDescriptorSerializable?,
)
