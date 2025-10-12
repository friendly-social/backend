package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class UserIdSerializable(val long: Long) {
    fun typed(): UserId = UserId(long)
}
