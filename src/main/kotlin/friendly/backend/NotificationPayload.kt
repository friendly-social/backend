package friendly.backend

sealed interface NotificationPayload {
    val toId: UserId

    data class NewRequest(
        override val toId: UserId,
        val fromId: UserId,
        val isMutual: Boolean,
    ) : NotificationPayload
}
