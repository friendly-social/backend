package friendly.backend

import io.ktor.client.HttpClient
import me.y9san9.graceful.GracefulScope

class Smtp2goContext(
    val token: Smtp2goToken,
    val httpClient: HttpClient,
    val gracefulScope: GracefulScope,
)
