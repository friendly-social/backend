package friendly.backend

import kotlin.random.Random

data class LoginCode private constructor(val int: Int) {
    fun serializable(): LoginCodeSerializable = LoginCodeSerializable(int)

    companion object {
        val Min: Int = 10_000_000
        val Max: Int = 99_999_999

        fun random(random: Random): LoginCode {
            val int = (Min..Max).random(random)
            return LoginCode(int)
        }

        fun orThrow(int: Int): LoginCode {
            require(int in Min..Max) {
                "Codes are exactly 8 digits long"
            }
            return LoginCode(int)
        }
    }
}
