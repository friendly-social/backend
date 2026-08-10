package friendly.backend

data class UserDescriptor(val id: UserId, val accessHash: UserAccessHash) {
    fun serializable(): UserDescriptorSerializable = UserDescriptorSerializable(
        id = id.serializable(),
        accessHash = accessHash.serializable(),
    )
}
