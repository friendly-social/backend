package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.time.Instant

object FriendTokensTable : Table("friend_tokens") {
    val tokenColumn = varchar("token", FriendToken.Length)
    val ownerIdColumn = long("owner_id")
    val expirationColumn = timestamp("expiration")

    override val primaryKey = PrimaryKey(tokenColumn, ownerIdColumn)

    suspend fun insert(
        ownerId: UserId,
        token: FriendToken,
        expiration: Instant,
    ) {
        insert { statement ->
            statement[tokenColumn] = token.string
            statement[ownerIdColumn] = ownerId.long
            statement[expirationColumn] = expiration
        }
    }

    suspend fun delete(ownerId: UserId) {
        deleteWhere { ownerIdColumn eq ownerId.long }
    }

    suspend fun select(ownerId: UserId): Entry? = selectAll()
        .where(ownerIdColumn eq ownerId.long)
        .firstOrNull()
        ?.toEntry()

    suspend fun select(token: FriendToken): Entry? = selectAll()
        .where(tokenColumn eq token.string)
        .firstOrNull()
        ?.toEntry()

    fun ResultRow.toEntry(): Entry = Entry(
        ownerId = UserId(this[ownerIdColumn]),
        token = FriendToken.orThrow(this[tokenColumn]),
        expiration = this[expirationColumn],
    )

    data class Entry(
        val ownerId: UserId,
        val token: FriendToken,
        val expiration: Instant,
    )
}
