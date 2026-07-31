package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException
import kotlin.random.Random

data class CommunityPostAccessHash private constructor(val string: String) {

    fun serializable(): CommunityPostAccessHashSerializable =
        CommunityPostAccessHashSerializable(string)

    companion object {
        val Length = 256

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-.~"

        fun random(random: Random): CommunityPostAccessHash {
            val string = buildString {
                repeat(Length) {
                    // We shouldn't use that pseudorandom LoL
                    append(Alphabet.random(random))
                }
            }
            return CommunityPostAccessHash(string)
        }

        fun orThrow(string: String): CommunityPostAccessHash {
            require(string.length == Length) {
                "Token should have $Length length, but was ${string.length}"
            }
            return CommunityPostAccessHash(string)
        }
    }
}

fun RoutingCall.postAccessHash(name: String): CommunityPostAccessHash {
    val string = parameters[name]
        ?: throw SerializationException(
            "Post access hash is not optional",
        )
    return CommunityPostAccessHashSerializable(string).typed()
}
