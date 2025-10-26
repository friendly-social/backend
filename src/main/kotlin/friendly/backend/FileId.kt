package friendly.backend

data class FileId(val long: Long) {
    fun serializable(): FileIdSerializable = FileIdSerializable(long)
}
