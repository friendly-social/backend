package friendly.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
sealed interface NotificationDetailsSerializable {
    fun typed(): NotificationDetails

    @Serializable
    @SerialName("new_request")
    data class NewRequest(val from: From, val isMutual: Boolean) :
        NotificationDetailsSerializable {
        override fun typed(): NotificationDetails =
            NotificationDetails.NewRequest(
                from = from.typed(),
                isMutual = isMutual,
            )

        @Serializable
        data class From(
            val id: UserIdSerializable,
            val accessHash: UserAccessHashSerializable,
            val avatar: FileDescriptorSerializable?,
            val nickname: NicknameSerializable,
        ) {
            fun typed(): NotificationDetails.NewRequest.From =
                NotificationDetails.NewRequest.From(
                    id = id.typed(),
                    accessHash = accessHash.typed(),
                    avatar = avatar?.typed(),
                    nickname = nickname.typed(),
                )
        }
    }

    @Serializable
    @SerialName("new_reply")
    data class NewReply(
        val id: CommunityPostIdSerializable,
        val accessHash: CommunityPostAccessHashSerializable,
        val owner: Owner,
        val textPreview: TextPreview,
    ) : NotificationDetailsSerializable {
        override fun typed(): NotificationDetails =
            NotificationDetails.NewReply(
                id = id.typed(),
                accessHash = accessHash.typed(),
                owner = owner.typed(),
                textPreview = textPreview.typed(),
            )

        @Serializable
        data class Owner(
            val id: UserIdSerializable,
            val accessHash: UserAccessHashSerializable,
            val avatar: FileDescriptorSerializable?,
            val nickname: NicknameSerializable,
        ) {
            fun typed(): NotificationDetails.NewReply.Owner =
                NotificationDetails.NewReply.Owner(
                    id = id.typed(),
                    accessHash = accessHash.typed(),
                    avatar = avatar?.typed(),
                    nickname = nickname.typed(),
                )
        }

        @JvmInline
        @Serializable
        value class TextPreview(val string: String) {
            private val maxLength get() =
                NotificationDetails.NewReply.TextPreview.MaxLength

            init {
                if (string.length > maxLength) {
                    throw SerializationException(
                        "NewReply.TextPreview should be $maxLength, was ${string.length}",
                    )
                }
            }

            fun typed(): NotificationDetails.NewReply.TextPreview =
                NotificationDetails.NewReply.TextPreview.orThrow(string)
        }
    }
}
