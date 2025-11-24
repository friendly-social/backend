package friendly.backend

data class NetworkDetails(
    val friends: List<UserDetails>,
    val connections: List<UserDetails>,
) {
    fun serializable(): NetworkDetailsSerializable = NetworkDetailsSerializable(
        friends = friends.map(UserDetails::serializable),
        connections = connections.map(UserDetails::serializable),
    )
}
