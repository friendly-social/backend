package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException

data class FilePreuploadId(val long: Long) {
    fun serializable(): FilePreuploadIdSerializable =
        FilePreuploadIdSerializable(long)
}

fun RoutingCall.filePreuploadId(name: String): FilePreuploadId {
    val string = parameters[name]
        ?: throw SerializationException(
            "$name is not optional",
        )
    val long = string.toLongOrNull()
        ?: throw SerializationException(
            "$name must be an integer",
        )
    return FilePreuploadIdSerializable(long).typed()
}
