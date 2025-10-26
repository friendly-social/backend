package friendly.backend

import kotlin.random.Random

data class FileAccessHash private constructor(val string: String) {

    fun serializable(): FileAccessHashSerializable =
        FileAccessHashSerializable(string)

    companion object {
        val Length = 256

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-.~"

        fun impureRandom(random: Random): FileAccessHash {
            val string = buildString {
                repeat(Length) {
                    // We shouldn't use that pseudorandom LoL
                    append(Alphabet.random(random))
                }
            }
            return FileAccessHash(string)
        }

        fun orThrow(string: String): FileAccessHash {
            require(string.length == Length) {
                "Token should have $Length length, but was ${string.length}"
            }
            return FileAccessHash(string)
        }
    }
}
