package friendly.backend

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert

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
}
