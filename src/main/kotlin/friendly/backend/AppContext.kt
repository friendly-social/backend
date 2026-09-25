package friendly.backend

import io.ktor.server.routing.Routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Experimental Approach to have god-object for DI instead of Map
 *
 * It's a good thing to try to maximize the amount of pure functions that
 * does not use this class. In fact, all the functions in the project must be
 * pure or accept AppContext.
 */
class AppContext {
    private var _database: R2dbcDatabase? = null
    val database: R2dbcDatabase
        get() = _database ?: error("Database is not configured.")
    fun provide(database: R2dbcDatabase) {
        check(_database == null) { "Database is already provided" }
        _database = database
    }

    private var _routing: Routing? = null
    val routing: Routing
        get() = _routing ?: error("Routing is not configured.")
    fun provide(routing: Routing) {
        check(_routing == null) { "Routing is already provided" }
        _routing = routing
    }

    private var _random: Random? = null
    val random: Random
        get() = _random ?: error("Random is not configured.")
    fun provide(random: Random) {
        check(_random == null) { "Random is already provided" }
        _random = random
    }

    private var _clock: Clock? = null
    val clock: Clock
        get() = _clock ?: error("Clock is not configured.")
    fun provide(clock: Clock) {
        check(_clock == null) { "Clock is already provided" }
        _clock = clock
    }

    private var _files: FilesContext? = null
    val files: FilesContext
        get() = _files ?: error("FilesContext is not configured.")
    fun provide(files: FilesContext) {
        check(_files == null) { "FilesContext is already provided" }
        _files = files
    }

    private var _notifications: NotificationsContext? = null
    val notifications: NotificationsContext
        get() = _notifications
            ?: error("NotificationsContext is not configured.")
    fun provide(notifications: NotificationsContext) {
        check(_notifications == null) {
            "NotificationsContext is already provided"
        }
        _notifications = notifications
    }

    private var _firebase: FirebaseContext? = null
    val firebase: FirebaseContext
        get() = _firebase ?: error("FirebaseContext is not configured.")
    fun provide(firebase: FirebaseContext?) {
        check(_firebase == null) { "FirebaseContext is already provided" }
        _firebase = firebase
    }

    private var _json: Json? = null
    val json: Json
        get() = _json ?: error("Json is not configured.")
    fun provide(json: Json) {
        check(_json == null) { "Json is already provided" }
        _json = json
    }

    private var _scope: CoroutineScope? = null
    val scope: CoroutineScope
        get() = _scope ?: error("CoroutineScope is not configured.")
    fun provide(scope: CoroutineScope) {
        check(_scope == null) { "CoroutineScope is already provided" }
        _scope = scope
    }

    private var _smtp2go: Smtp2goContext? = null
    val smtp2go: Smtp2goContext
        get() = _smtp2go ?: error("Smtp2go is not configured.")
    fun provide(smtp2go: Smtp2goContext) {
        check(_smtp2go == null) { "Smtp2goContext is already provided" }
        _smtp2go = smtp2go
    }

    private var _s3: S3Context? = null
    val s3: S3Context
        get() = _s3 ?: error("S3 is not configured")
    fun provide(s3: S3Context?) {
        check(_s3 == null) { "S3Context is already provided" }
        _s3 = s3
    }
}
