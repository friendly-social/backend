package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object TokensTable : Table("tokens") {
    private val tokenColumn = varchar("token", Token.Length)
    private val ownerIdColumn = long("owner_id")

    override val primaryKey = PrimaryKey(tokenColumn, ownerIdColumn)

    suspend fun impureInsert(token: Token, ownerId: UserId) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
        }
    }

    suspend fun impureExists(token: Token, ownerId: UserId): Boolean {
        val entry = selectAll().where(
            (tokenColumn eq token.string) and
                (ownerIdColumn eq ownerId.long),
        ).firstOrNull()
        return entry != null
    }
}
