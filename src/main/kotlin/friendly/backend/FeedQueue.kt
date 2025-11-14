package friendly.backend

data class FeedQueue(val entries: List<Entry>) {
    fun serializable(): FeedQueueSerializable {
        val entries = entries.map { entry -> entry.serializable() }
        return FeedQueueSerializable(entries)
    }

    data class Entry(
        val isExtendedNetwork: Boolean,
        val commonFriends: List<UserDetails>,
        val details: UserDetails,
    ) {
        fun serializable(): FeedQueueSerializable.Entry =
            FeedQueueSerializable.Entry(
                isExtendedNetwork = isExtendedNetwork,
                commonFriends = commonFriends.map { user ->
                    user.serializable()
                },
                details = details.serializable(),
            )
    }
}
