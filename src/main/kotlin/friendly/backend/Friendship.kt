package friendly.backend

sealed interface Friendship {
    fun serializable(): FriendshipSerializable

    data object Friends : Friendship {
        override fun serializable(): FriendshipSerializable =
            FriendshipSerializable(string = "friends")
    }
    data object IncomingRequest : Friendship {
        override fun serializable(): FriendshipSerializable =
            FriendshipSerializable(string = "incomingRequest")
    }
    data object OutgoingRequest : Friendship {
        override fun serializable(): FriendshipSerializable =
            FriendshipSerializable(string = "outgoingRequest")
    }
    data object Block : Friendship {
        override fun serializable(): FriendshipSerializable =
            FriendshipSerializable(string = "block")
    }
    data object None : Friendship {
        override fun serializable(): FriendshipSerializable =
            FriendshipSerializable(string = "none")
    }
}
