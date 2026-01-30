package friendly.backend

sealed interface NotificationDetails {
    fun serializable(): NotificationDetailsSerializable

    data class NewRequest(val from: UserDetails, val isMutual: Boolean) :
        NotificationDetails {
        override fun serializable(): NotificationDetailsSerializable =
            NotificationDetailsSerializable.NewRequest(
                from = from.serializable(),
                isMutual = isMutual,
            )
    }
}
