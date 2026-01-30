package friendly.backend

data class Interest private constructor(val string: String) {
    fun serializable(): InterestSerializable = InterestSerializable(string)

    companion object {
        val MaxLength: Int = 64

        fun orThrow(string: String): Interest {
            require(string.length <= MaxLength)
            return Interest(string)
        }
    }
}
