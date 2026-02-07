package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
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

    suspend fun insert(token: Token, ownerId: UserId) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
        }
    }

    suspend fun updateFirebase(
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

    suspend fun delete(ownerId: UserId, token: Token) {
        deleteWhere {
            (ownerIdColumn eq ownerId.long) and (tokenColumn eq token.string)
        }
    }

    suspend fun exists(token: Token, ownerId: UserId): Boolean {
        val entry = selectAll().where(
            (tokenColumn eq token.string) and
                (ownerIdColumn eq ownerId.long),
        ).firstOrNull()
        return entry != null
    }

    suspend fun select(ownerId: UserId): List<Entry> = selectAll()
        .where(ownerIdColumn eq ownerId.long)
        .map { row -> row.toEntry() }
        .toList()

    suspend fun deleteFirebase(token: FirebaseToken) =
        deleteWhere { firebaseTokenColumn eq token.string }

    data class Entry(
        val ownerId: UserId,
        val token: Token,
        val firebaseToken: FirebaseToken? = null,
    )

    private fun ResultRow.toEntry(): Entry {
        val ownerId = UserId(this[ownerIdColumn])
        val token = Token.orThrow(this[tokenColumn])
        val firebaseToken = this[firebaseTokenColumn]
            ?.let(FirebaseToken::orThrow)
        return Entry(ownerId, token, firebaseToken)
    }
}
