package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

object TokensTable : Table("tokens") {
    private val tokenColumn = varchar("token", Token.Length)
    private val ownerIdColumn = long("owner_id")

    private val firebaseTokenColumn =
        varchar("firebase_token", FirebaseToken.MaxLength).nullable()

    override val primaryKey = PrimaryKey(tokenColumn, ownerIdColumn)

    suspend fun impureInsert(token: Token, ownerId: UserId) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
        }
    }

    suspend fun impureUpdateFirebase(
        ownerId: UserId,
        token: Token,
        firebaseToken: FirebaseToken,
    ) {
        update(
            where = {
                (ownerIdColumn eq ownerId.long) and
                    (tokenColumn eq token.string)
            },
        ) { statement ->
            statement[firebaseTokenColumn] = firebaseToken.string
        }
    }

    suspend fun impureDelete(ownerId: UserId, token: Token) {
        deleteWhere {
            (ownerIdColumn eq ownerId.long) and (tokenColumn eq token.string)
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
