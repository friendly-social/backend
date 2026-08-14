package friendly.backend

data class ActivityId(val long: Long) {
    fun serializable(): ActivityIdSerializable = ActivityIdSerializable(long)
}

inline fun CursorId.toActivityId(fallback: () -> Nothing): ActivityId? =
    ActivityId(string.toLongOrNull() ?: fallback())
