package friendly.backend

/**
 * July feature to publicly invite people in Friendly.
 * It has a bunch of hardcoded invite links that are active in July 2026.
 */
object PermanentLinks {
    val Vestibule = Record(
        token = FriendToken.orThrow("VESTIBULENETWORK"),
        userId = UserId(904),
    )

    val Ru = Record(
        token = FriendToken.orThrow("RUSPEAKINNETWORK"),
        userId = UserId(905),
    )

    val Flat = Record(
        token = FriendToken.orThrow("FLATFRIENNETWORK"),
        userId = UserId(1175),
    )

    val Entries = listOf(Vestibule, Ru, Flat)

    data class Record(val token: FriendToken, val userId: UserId)
}
