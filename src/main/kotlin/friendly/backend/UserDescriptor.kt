package friendly.backend

data class UserDescriptor(val id: UserId, val accessHash: UserAccessHash) {
    fun serializable(): UserDescriptorSerializable = UserDescriptorSerializable(
        id = id.serializable(),
        accessHash = accessHash.serializable(),
    )

    companion object {
        fun parseOrNull(string: String): UserDescriptor? {
            val separator = string.indexOf(':')
            if (separator < 0) return null
            val id = string.substring(0, separator).toLongOrNull() ?: return null
            val accessHash = string.substring(separator + 1)
            if (accessHash.length != UserAccessHash.Length) return null
            return UserDescriptor(UserId(id), UserAccessHash.orThrow(accessHash))
        }
    }
}
