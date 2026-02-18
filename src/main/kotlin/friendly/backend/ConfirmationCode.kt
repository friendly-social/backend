package friendly.backend

import kotlin.random.Random

data class ConfirmationCode private constructor(val int: Int) {
    operator fun get(index: Int): Int {
        require(index in 0..<8)
        var result = int
        repeat(7 - index) {
            result /= 10
        }
        return result % 10
    }

    fun serializable(): ConfirmationCodeSerializable =
        ConfirmationCodeSerializable(int)

    companion object {
        val Min: Int = 10_000_000
        val Max: Int = 99_999_999

        fun random(random: Random): ConfirmationCode {
            val int = (Min..Max).random()
            return ConfirmationCode(int)
        }

        fun orThrow(int: Int): ConfirmationCode {
            require(int in Min..Max) {
                "Codes are exactly 8 digits long"
            }
            return ConfirmationCode(int)
        }
    }
}
