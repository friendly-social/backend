package friendly.backend

import kotlin.random.Random

data class Token private constructor(val string: String) {
    fun serializable(): TokenSerializable = TokenSerializable(string)

    companion object {
        val Length = 256

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-.~"

        fun random(random: Random): Token {
            val string = buildString {
                repeat(Length) {
                    append(Alphabet.random(random))
                }
            }
            return Token(string)
        }

        fun orThrow(string: String): Token {
            require(string.length == Length) {
                "Token should have $Length length, but was ${string.length}"
            }
            return Token(string)
        }
    }
}
