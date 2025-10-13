package friendly.backend

import friendly.backend.Token
import friendly.backend.UserId
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert

object TokensTable : Table("tokens") {
    private val tokenColumn = varchar("token", Token.Length)
    private val ownerIdColumn = long("owner_id")

    override val primaryKey = PrimaryKey(tokenColumn, ownerIdColumn)

    suspend fun insert(token: Token, ownerId: UserId) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
        }
    }
}
