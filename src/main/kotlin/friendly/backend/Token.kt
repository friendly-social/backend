package friendly.backend

data class Token private constructor(val string: String) {
    fun serializable(): TokenSerializable = TokenSerializable(string)

    companion object {
        val Length = 256

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-.~"

        // todo: make it a pure function
        fun random(): Token {
            val string = buildString {
                repeat(Length) {
                    // We shouldn't use that pseudorandom LoL
                    append(Alphabet.random())
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
