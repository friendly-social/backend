package friendly.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import me.y9san9.graceful.gracefulScope

suspend fun bootstrapSmtp2go(block: suspend (Smtp2goContext) -> Unit) {
    val token = System
        .getenv("FRIENDLY_SMTP2GO_TOKEN")
        .let(::Smtp2goToken)
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
    gracefulScope { scope ->
        val context = Smtp2goContext(token, httpClient, scope)
        block(context)
    }
}
