package friendly.backend

sealed interface NotificationEntry {
    val id: NotificationId
    val toId: UserId

    data class NewRequest(
        override val id: NotificationId,
        override val toId: UserId,
        val fromId: UserId,
        val isMutual: Boolean,
    ) : NotificationEntry
}
