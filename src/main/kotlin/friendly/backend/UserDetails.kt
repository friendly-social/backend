package friendly.backend

data class UserDetails(
    val id: UserId,
    val accessHash: UserAccessHash,
    val nickname: Nickname,
    val email: Email?,
    val description: UserDescription,
    val interests: InterestList,
    val avatar: FileDescriptor?,
    val socialLink: SocialLink?,
) {
    fun serializable(): UserDetailsSerializable = UserDetailsSerializable(
        id = id.serializable(),
        accessHash = accessHash.serializable(),
        nickname = nickname.serializable(),
        email = email?.serializable(),
        description = description.serializable(),
        interests = interests.serializable(),
        avatar = avatar?.serializable(),
        socialLink = socialLink?.serializable(),
    )
}
