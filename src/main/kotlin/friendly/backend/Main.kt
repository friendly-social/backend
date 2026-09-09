package friendly.backend

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.lang.Thread
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

private val logger = LoggerFactory.getLogger("Friendly")

suspend fun main(): Unit = coroutineScope {
    val scope = this
    val port = System.getenv("FRIENDLY_PORT")?.toInt() ?: 8080
    val database = bootstrapDatabase()
    val files = bootstrapFiles()
    val firebase = bootstrapFirebase()

    bootstrapSmtp2go { smtp2go ->
        bootstrapNotifications { notifications ->
            val context = AppContext(
                database = database,
                random = Random,
                clock = Clock.System,
                files = files,
                notifications = notifications,
                firebase = firebase,
                scope = scope,
                json = Json,
                smtp2go = smtp2go,
            )
            NotificationsService.restoreScheduled(context)
            FilesCleanupService.attach(context)
            val server = embeddedServer(port, context)
            addShutdownHook(server, notifications, smtp2go)
            server.start()
            awaitCancellation()
        }
    }
}

private fun embeddedServer(
    port: Int,
    context: AppContext,
): EmbeddedServer<*, *> = embeddedServer(Netty, port) {
    installStatusPages()
    installContentNegotiation()
    installCors()
    installCallLogging()
    installCallId()

    routing {
        val context = context.copy(routing = this)
        AuthRouting.generate(context)
        AuthRouting.firebase(context)
        AuthRouting.email(context)
        AuthRouting.login(context)
        AuthRouting.logout(context)
        UsersRouting.details(context)
        UsersRouting.details2(context)
        UsersRouting.edit(context)
        FilesRouting.upload(context)
        FilesRouting.download(context)
        FriendsRouting.generate(context)
        FriendsRouting.generateForce(context)
        FriendsRouting.add(context)
        FriendsRouting.request(context)
        FriendsRouting.decline(context)
        NetworkRouting.details(context)
        FeedRouting.queue(context)
        EmailRouting.link(context)
        EmailRouting.unlink(context)
        EmailRouting.confirm(context)
        CommunityRouting.post(context)
        CommunityRouting.list(context)
        CommunityRouting.delete(context)
        CommunityRouting.edit(context)
        CommunityRouting.replies(context)
        CommunityRouting.from(context)
        CommunityRouting.details(context)
        CommunityRouting.details2(context)
        ActivityRouting.list(context)
        ActivityRouting.read(context)
        NotificationsRouting.details(context)
    }
}

private fun Application.installStatusPages() {
    install(StatusPages) {
        exception<SerializationException> { call, exception ->
            call.respondText(
                text = exception.message.orEmpty(),
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<BadRequestException> { call, badRequest ->
            val message = when (val cause = badRequest.cause) {
                is ContentConvertException,
                is SerializationException,
                -> cause.message
                else -> badRequest.message
            }
            call.respondText(
                text = message.orEmpty(),
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

private fun Application.installCors() {
    install(CORS) {
        anyHost()
        anyMethod()
        allowHeader("X-Locale")
        allowHeader("X-Token")
        allowHeader("X-User-Id")
        allowNonSimpleContentTypes = true
    }
}

private fun Application.installCallLogging() {
    install(CallLogging) {
        level = INFO
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val id = call.callId ?: "N/A"
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$method $path -> Status: $status in ${duration}ms ($id)"
        }
    }
}

private fun Application.installCallId() {
    install(CallId) {
        generate()
        header(HttpHeaders.XRequestId)
    }
}

private fun addShutdownHook(
    server: EmbeddedServer<*, *>,
    notifications: NotificationsContext,
    smtp2go: Smtp2goContext,
) {
    val shutdownHook = Thread {
        runBlocking {
            logger.info("Shutdown hook intercepted...")
            logger.info("Stopping ktor server...")
            measureTime {
                server.stop(1_000, 20_000)
            }.let { time ->
                println("Stopped in $time")
            }
            logger.info("Stopping notifications actor...")
            measureTime {
                notifications.gracefulScope.stop(
                    cooldownTimeout = 30.seconds,
                    cancellationTimeout = 30.seconds,
                )
            }.let { time ->
                println("Stopped in $time")
            }
            logger.info("Stopping smtp2go actor...")
            measureTime {
                smtp2go.gracefulScope.stop(
                    cooldownTimeout = 30.seconds,
                    cancellationTimeout = 30.seconds,
                )
            }.let { time ->
                println("Stopped in $time")
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)
}
