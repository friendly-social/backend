package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlin.random.Random
import kotlin.time.Clock

suspend fun main() {
    val port = System.getenv("FRIENDLY_PORT")?.toInt() ?: 8080
    val database = impureBootstrapDatabase()
    val files = impureBootstrapFiles()

    embeddedServer(Netty, port) {
        installStatusPages()
        installContentNegotiation()

        routing {
            val context = AppContext(
                database = database,
                routing = this,
                random = Random,
                clock = Clock.System,
                files = files,
            )
            AuthRouting.impureGenerate(context)
            UsersRouting.impureDetails(context)
            FilesRouting.impureUpload(context)
            FilesRouting.impureDownload(context)
            FriendsRouting.impureGenerate(context)
            FriendsRouting.impureAdd(context)
            NetworkRouting.impureDetails(context)
            FeedRouting.impureQueue(context)
            ConnectionsRouting.impureRequest(context)
            ConnectionsRouting.impureDecline(context)
        }
    }.start(wait = true)
}

private fun Application.installStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, badRequest ->
            val message = when (val cause = badRequest.cause) {
                is ContentConvertException,
                is SerializationException,
                -> cause.message
                else -> badRequest.message
            }
            call.respondText(
                text = "400: $message",
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<Throwable> { call, throwable ->
            throwable.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}

private fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}
