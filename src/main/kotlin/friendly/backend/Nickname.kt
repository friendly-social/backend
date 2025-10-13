package friendly.backend

data class Nickname private constructor(val string: String) {
    fun serializable(): NicknameSerializable = NicknameSerializable(string)

    companion object {
        val MaxLength = 256

        fun orThrow(string: String): Nickname {
            require(string.length <= MaxLength)
            return Nickname(string)
        }
    }
}
