package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlin.random.Random

data class FilePreuploadAccessHash private constructor(val string: String) {

    fun serializable(): FilePreuploadAccessHashSerializable =
        FilePreuploadAccessHashSerializable(string)

    companion object {
        val Length = 256

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-.~"

        fun random(random: Random): FilePreuploadAccessHash {
            val string = buildString {
                repeat(Length) {
                    // We shouldn't use that pseudorandom LoL
                    append(Alphabet.random(random))
                }
            }
            return FilePreuploadAccessHash(string)
        }

        fun orThrow(string: String): FilePreuploadAccessHash {
            require(string.length == Length) {
                "Token should have $Length length, but was ${string.length}"
            }
            return FilePreuploadAccessHash(string)
        }
    }
}

fun RoutingCall.filePreuploadAccessHash(name: String): FilePreuploadAccessHash {
    val string = parameters[name] ?: error("$name is not optional")
    return FilePreuploadAccessHashSerializable(string).typed()
}
