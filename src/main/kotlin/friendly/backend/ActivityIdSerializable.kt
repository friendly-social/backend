package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ActivityIdSerializable(val long: Long) {
    fun typed(): ActivityId = ActivityId(long)
}
