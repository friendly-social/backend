package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

suspend fun bootstrapDatabase(): R2dbcDatabase {
    val url = System.getenv("FRIENDLY_DATABASE_URL")
        ?: error("Provide 'FRIENDLY_DATABASE_URL' env")

    val db = R2dbcDatabase.connect(
        url = url,
        driver = "postgresql",
    )

    createTables(db)

    return db
}

suspend fun createTables(db: R2dbcDatabase) {
    // TODO: it's broken in the current version of exposed
    // suspendTransaction(db) {
    //     SchemaUtils.create(
    //         TokensStorage,
    //         UsersStorage,
    //         InterestsStorage,
    //     )
    // }
}
