package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class FeedQueueSerializable(val entries: List<Entry>) {
    fun typed(): FeedQueue {
        val entries = entries.map { entry -> entry.typed() }
        return FeedQueue(entries)
    }

    @Serializable
    data class Entry(
        val isRequest: Boolean,
        val isExtendedNetwork: Boolean,
        val commonFriends: List<UserDetailsSerializable>,
        val details: UserDetailsSerializable,
    ) {
        fun typed(): FeedQueue.Entry = FeedQueue.Entry(
            isRequest = isRequest,
            isExtendedNetwork = isExtendedNetwork,
            commonFriends = commonFriends.map { friend -> friend.typed() },
            details = details.typed(),
        )
    }
}
