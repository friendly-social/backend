package friendly.backend

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig

fun bootstrapDatabase(): R2dbcDatabase {
    val url = System.getenv("FRIENDLY_DATABASE_URL")
        ?: error("Provide 'FRIENDLY_DATABASE_URL' env")

    val factory = ConnectionFactories.get(url)
    val config = ConnectionPoolConfiguration.builder(factory).build()
    val pool = ConnectionPool(config)
    val r2dbc = R2dbcDatabaseConfig.Builder().apply {
        explicitDialect = PostgreSQLDialect()
    }

    return R2dbcDatabase.connect(pool, r2dbc)
}
