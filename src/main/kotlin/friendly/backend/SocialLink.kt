package friendly.backend

data class SocialLink private constructor(val string: String) {
    fun serializable(): SocialLinkSerializable = SocialLinkSerializable(string)

    companion object {
        val MaxLength = 2048

        fun orThrow(string: String): SocialLink {
            require(string.length <= MaxLength)
            return SocialLink(string)
        }
    }
}
