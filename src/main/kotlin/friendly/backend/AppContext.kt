package friendly.backend

import kotlin.time.Clock
import io.ktor.server.routing.Routing
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import kotlin.random.Random

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
}
