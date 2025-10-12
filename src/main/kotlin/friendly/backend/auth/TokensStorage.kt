package friendly.backend.auth

import friendly.backend.Token
import friendly.backend.UserId
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert

object TokensStorage : Table("tokens") {
    private val idColumn = long("id").autoIncrement()
    private val tokenColumn = varchar("token", Token.Length)
    private val ownerIdColumn = long("owner_id")

    suspend fun insert(token: Token, ownerId: UserId) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
        }
    }
}
