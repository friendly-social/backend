package friendly.backend

data class FileSize private constructor(val bytes: Long) :
    Comparable<FileSize> {

    fun minusOrZero(other: FileSize): FileSize {
        if (other.bytes >= this.bytes) {
            return FileSize.Zero
        }
        return orThrow(bytes = this.bytes - other.bytes)
    }

    override operator fun compareTo(other: FileSize): Int =
        this.bytes.compareTo(other.bytes)

    companion object {
        val Zero: FileSize = 0L.bytes
        val MaxPerIp: FileSize = 20L.MB

        fun orThrow(bytes: Long): FileSize {
            require(bytes >= 0) { "FileSize cannot be negative, was $bytes" }
            return FileSize(bytes)
        }
    }
}

val Long.MB: FileSize get() = (this * 1_024).KB
val Long.KB: FileSize get() = (this * 1_024).bytes
val Long.bytes: FileSize get() = FileSize.orThrow(bytes = this)

fun FileSize?.orZero(): FileSize = this ?: FileSize.Zero
