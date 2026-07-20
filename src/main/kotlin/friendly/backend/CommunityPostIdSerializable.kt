package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CommunityPostIdSerializable(val long: Long) {
    fun typed(): CommunityPostId = CommunityPostId(long)
}
