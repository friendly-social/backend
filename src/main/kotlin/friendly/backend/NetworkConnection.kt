package friendly.backend

data class NetworkConnection(
    val degree: NetworkDegree,
    val fromId: UserId,
    val toId: UserId,
)
