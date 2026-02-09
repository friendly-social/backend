package friendly.backend

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class LoginCodeExpiration(val instant: Instant) {
    companion object {
        val Duration: Duration = 20.minutes

        fun createdNow(now: Instant): LoginCodeExpiration {
            val instant = now + Duration
            return LoginCodeExpiration(instant)
        }
    }
}
