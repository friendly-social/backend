package friendly.backend

data class ConfirmationCodeAttempts private constructor(val int: Int) {
    fun incrementOrThrow(): ConfirmationCodeAttempts {
        val int = this.int + 1
        return ConfirmationCodeAttempts.orThrow(int)
    }

    companion object {
        val Max: Int = 5

        fun orThrow(int: Int): ConfirmationCodeAttempts {
            require(int >= 0) { "Negative code attempts" }
            require(int <= Max) { "There may not be more attempts than $Max" }
            return ConfirmationCodeAttempts(int)
        }
    }
}
