package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlin.random.Random

data class FriendToken private constructor(val string: String) {
    fun serializable(): FriendTokenSerializable =
        FriendTokenSerializable(string)

    companion object {
        val Length = 16

        val Alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"

        fun random(random: Random): FriendToken {
            val string = buildString {
                repeat(Length) {
                    append(Alphabet.random(random))
                }
            }
            return FriendToken(string)
        }

        fun orThrow(string: String): FriendToken {
            require(string.length == Length) {
                "FriendToken should have $Length length, but was ${string.length}"
            }
            return FriendToken(string)
        }
    }
}

fun RoutingCall.friendToken(name: String): FriendToken {
    val string = parameters[name] ?: error("$name is not optional")
    return FriendTokenSerializable(string).typed()
}
