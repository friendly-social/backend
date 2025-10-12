package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

/**
 * Experimental Approach to have god-object for DI instead of Map
 */
data class AppContext(val db: R2dbcDatabase)
