package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class CursorSerializable<out T>(
    val data: List<T>,
    val nextId: CursorIdSerializable?,
) {
    inline fun <R> typed(block: (T) -> R): Cursor<R> = Cursor(
        data = data.map { element -> block(element) },
        nextId = nextId?.typed(),
    )
}
