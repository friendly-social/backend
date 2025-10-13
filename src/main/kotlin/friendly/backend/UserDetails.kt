package friendly.backend

data class UserDetails(
    val id: UserId,
    val nickname: Nickname,
    val description: UserDescription,
    val interests: List<Interest>,
)
