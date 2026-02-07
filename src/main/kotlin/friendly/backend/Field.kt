package friendly.backend

/**
 * Simple wrapper for any [value] that is useful when you want to distinguish
 * explicitly set `null` value and undefined value. That is handy when it comes
 * to passing which fields should be edited and which should be left.
 */
data class Field<out T>(val value: T) {
    inline fun <R> serializable(block: (T) -> R): FieldSerializable<R> =
        FieldSerializable(block(value))
}
