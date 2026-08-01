package friendly.backend

data class UserDescriptorSerializable(
    val id: UserIdSerializable,
    val accessHash: UserAccessHashSerializable,
) {
    fun typed(): UserDescriptor = UserDescriptor(
        id = id.typed(),
        accessHash = accessHash.typed(),
    )
}
