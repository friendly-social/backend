package friendly.backend

data class LoginCodeAttempts private constructor(val int: Int) {
    fun incrementOrThrow(): LoginCodeAttempts {
        val int = this.int + 1
        return LoginCodeAttempts.orThrow(int)
    }

    companion object {
        val Max: Int = 5

        fun orThrow(int: Int): LoginCodeAttempts {
            require(int >= 0) { "Negative code attempts" }
            require(int <= Max) { "There may not be more attempts than $Max" }
            return LoginCodeAttempts(int)
        }
    }
}
