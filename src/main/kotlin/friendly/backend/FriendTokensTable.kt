package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object FriendTokensTable : Table("friend_tokens") {
    val tokenColumn = varchar("token", FriendToken.Length)
    val ownerIdColumn = long("owner_id")

    override val primaryKey = PrimaryKey(tokenColumn, ownerIdColumn)

    suspend fun impureInsert(token: FriendToken, ownerId: UserId) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
        }
    }

    suspend fun impureDelete(ownerId: UserId) {
        deleteWhere { ownerIdColumn eq ownerId.long }
    }

    suspend fun impureExists(ownerId: UserId, token: FriendToken): Boolean {
        val entry = selectAll()
            .where(
                (tokenColumn eq token.string) and
                    (ownerIdColumn eq ownerId.long),
            ).firstOrNull()
        return entry != null
    }
}
