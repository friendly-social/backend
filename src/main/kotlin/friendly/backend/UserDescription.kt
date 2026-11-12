package friendly.backend

data class UserDescription private constructor(val string: String) {
    fun serializable(): UserDescriptionSerializable =
        UserDescriptionSerializable(string)

    companion object {
        val MaxLength: Int = 10_240

        fun orThrow(string: String): UserDescription {
            require(string.length <= MaxLength)
            require(string.isNotBlank())
            return UserDescription(string)
        }
    }
}
