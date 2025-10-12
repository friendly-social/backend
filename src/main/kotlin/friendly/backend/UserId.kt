package friendly.backend

data class UserId(val long: Long) {
    fun serializable(): UserIdSerializable = UserIdSerializable(long)
}
