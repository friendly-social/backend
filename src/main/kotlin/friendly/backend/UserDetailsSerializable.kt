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
    val socialLink: SocialLinkSerializable?,
) {
    fun typed(): UserDetails = UserDetails(
        id = id.typed(),
        accessHash = accessHash.typed(),
        nickname = nickname.typed(),
        description = description.typed(),
        interests = interests.map { interest -> interest.typed() },
        avatar = avatar?.typed(),
        socialLink = socialLink?.typed(),
    )
}
