package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlin.random.Random

data class UserAccessHash private constructor(val string: String) {

    fun serializable(): UserAccessHashSerializable =
        UserAccessHashSerializable(string)

    companion object {
        val Length = 256

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-.~"

        fun random(random: Random): UserAccessHash {
            val string = buildString {
                repeat(Length) {
                    append(Alphabet.random(random))
                }
            }
            return UserAccessHash(string)
        }

        fun orThrow(string: String): UserAccessHash {
            require(string.length == Length) {
                "Token should have $Length length, but was ${string.length}"
            }
            return UserAccessHash(string)
        }
    }
}

fun RoutingCall.userAccessHash(name: String): UserAccessHash {
    val string = parameters[name] ?: error("$name is not optional")
    return UserAccessHashSerializable(string).typed()
}
