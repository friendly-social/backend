package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class UserDetailsSerializable(
    val id: UserIdSerializable,
    val accessHash: UserAccessHashSerializable,
    val nickname: NicknameSerializable,
    val email: EmailSerializable?,
    val description: UserDescriptionSerializable,
    val interests: InterestListSerializable,
    val avatar: FileDescriptorSerializable?,
    val socialLink: SocialLinkSerializable?,
) {
    fun typed(): UserDetails = UserDetails(
        id = id.typed(),
        accessHash = accessHash.typed(),
        nickname = nickname.typed(),
        email = email?.typed(),
        description = description.typed(),
        interests = interests.typed(),
        avatar = avatar?.typed(),
        socialLink = socialLink?.typed(),
    )
}
