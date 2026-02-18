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
class AppContext(
    database: R2dbcDatabase? = null,
    routing: Routing? = null,
    random: Random? = null,
    clock: Clock? = null,
    files: FilesContext? = null,
    notifications: NotificationsContext? = null,
    firebase: FirebaseContext? = null,
    json: Json? = null,
    scope: CoroutineScope? = null,
    smtp2go: Smtp2goContext? = null,
) {
    private val _database = database
    val database: R2dbcDatabase
        get() = _database ?: error("Database is not configured.")

    private val _routing = routing
    val routing: Routing
        get() = _routing ?: error("Routing is not configured.")

    private val _random = random
    val random: Random
        get() = _random ?: error("Random is not configured.")

    private val _clock = clock
    val clock: Clock
        get() = _clock ?: error("Clock is not configured.")

    private val _files = files
    val files: FilesContext
        get() = _files ?: error("FilesContext is not configured.")

    private val _notifications = notifications
    val notifications: NotificationsContext
        get() = _notifications
            ?: error("NotificationsContext is not configured.")

    private val _firebase = firebase
    val firebase: FirebaseContext
        get() = _firebase
            ?: error("FirebaseContext is not configured.")

    private val _json = json
    val json: Json
        get() = _json
            ?: error("Json is not configured.")

    private val _scope = scope
    val scope: CoroutineScope
        get() = _scope ?: error("CoroutineScope is not configured.")

    private val _smtp2go = smtp2go
    val smtp2go: Smtp2goContext
        get() = _smtp2go ?: error("Smtp2go is not configured.")

    fun copy(
        database: R2dbcDatabase? = null,
        routing: Routing? = null,
        random: Random? = null,
        clock: Clock? = null,
        files: FilesContext? = null,
        notifications: NotificationsContext? = null,
        firebase: FirebaseContext? = null,
        json: Json? = null,
        scope: CoroutineScope? = null,
        smtp2go: Smtp2goContext? = null,
    ): AppContext = AppContext(
        database = database ?: _database,
        routing = routing ?: _routing,
        random = random ?: _random,
        clock = clock ?: _clock,
        files = files ?: _files,
        notifications = notifications ?: _notifications,
        firebase = firebase ?: _firebase,
        json = json ?: _json,
        scope = scope ?: _scope,
        smtp2go = smtp2go ?: _smtp2go,
    )
}
