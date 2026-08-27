package friendly.backend

sealed interface NotificationDetails {
    fun serializable(): NotificationDetailsSerializable

    data class NewRequest(val from: From, val isMutual: Boolean) :
        NotificationDetails {
        override fun serializable(): NotificationDetailsSerializable =
            NotificationDetailsSerializable.NewRequest(
                from = from.serializable(),
                isMutual = isMutual,
            )

        data class From(
            val id: UserId,
            val accessHash: UserAccessHash,
            val avatar: FileDescriptor?,
            val nickname: Nickname,
        ) {
            @Suppress("ktlint:standard:max-line-length")
            fun serializable(): NotificationDetailsSerializable.NewRequest.From =
                NotificationDetailsSerializable.NewRequest.From(
                    id = id.serializable(),
                    accessHash = accessHash.serializable(),
                    avatar = avatar?.serializable(),
                    nickname = nickname.serializable(),
                )
        }
    }

    data class NewReply(
        val id: CommunityPostId,
        val accessHash: CommunityPostAccessHash,
        val owner: Owner,
        val textPreview: TextPreview,
    ) : NotificationDetails {
        override fun serializable(): NotificationDetailsSerializable =
            NotificationDetailsSerializable.NewReply(
                id = id.serializable(),
                accessHash = accessHash.serializable(),
                owner = owner.serializable(),
                textPreview = textPreview.serializable(),
            )

        data class Owner(
            val id: UserId,
            val accessHash: UserAccessHash,
            val avatar: FileDescriptor?,
            val nickname: Nickname,
        ) {
            @Suppress("ktlint:standard:max-line-length")
            fun serializable(): NotificationDetailsSerializable.NewReply.Owner =
                NotificationDetailsSerializable.NewReply.Owner(
                    id = id.serializable(),
                    accessHash = accessHash.serializable(),
                    avatar = avatar?.serializable(),
                    nickname = nickname.serializable(),
                )
        }

        data class TextPreview private constructor(val string: String) {
            @Suppress("ktlint:standard:max-line-length")
            fun serializable(): NotificationDetailsSerializable.NewReply.TextPreview =
                NotificationDetailsSerializable.NewReply.TextPreview(string)

            companion object {
                val MaxLength: Int = 100

                fun orTrim(string: String): TextPreview {
                    if (string.length > MaxLength) {
                        return orThrow(
                            string = string.substring(0..MaxLength - 1) + "…",
                        )
                    }
                    return orThrow(string)
                }

                fun orThrow(string: String): TextPreview {
                    require(string.length <= MaxLength)
                    return TextPreview(string)
                }
            }
        }
    }
}
