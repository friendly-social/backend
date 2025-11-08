package friendly.backend

data class NetworkDetails(val friends: List<UserDetails>) {
    fun serializable(): NetworkDetailsSerializable = NetworkDetailsSerializable(
        friends = friends.map(UserDetails::serializable),
    )
}
