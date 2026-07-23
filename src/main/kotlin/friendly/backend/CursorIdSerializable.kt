package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CursorIdSerializable(val string: String) {
    fun typed(): CursorId = CursorId(string)
}
