package friendly.backend

import kotlinx.serialization.Serializable

/**
 * Simple wrapper for any [value] that is useful when you want to distinguish
 * explicitly set `null` value and undefined value. That is handy when it comes
 * to passing which fields should be edited and which should be left.
 */
@Serializable
data class FieldSerializable<out T>(val value: T) {
    inline fun <R> typed(block: (T) -> R): Field<R> = Field(block(value))
}
