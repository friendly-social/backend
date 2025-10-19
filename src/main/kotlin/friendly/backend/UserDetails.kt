package friendly.backend

data class UserDetails(
    val id: UserId,
    val accessHash: UserAccessHash,
    val nickname: Nickname,
    val description: UserDescription,
    val interests: List<Interest>,
)
