package friendly.backend

data class FirebaseToken private constructor(val string: String) {
    fun serializable(): FirebaseTokenSerializable =
        FirebaseTokenSerializable(string = string)

    companion object {
        // There's no official limit and usually tokens are sub-256
        val MaxLength: Int = 4096

        fun orThrow(string: String): FirebaseToken {
            require(string.length <= MaxLength) {
                "Firebase token is too large to be stored."
            }
            return FirebaseToken(string)
        }
    }
}
