package friendly.backend

data class UserDescription private constructor(val string: String) {
    fun serializable(): UserDescriptionSerializable =
        UserDescriptionSerializable(string)

    companion object {
        val MaxLength: Int = 1_024

        fun orThrow(string: String): UserDescription {
            require(string.length <= MaxLength)
            return UserDescription(string)
        }
    }
}
