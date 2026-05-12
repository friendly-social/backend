package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object EmailsTable : Table("emails") {
    private val ownerIdColumn = long("owner_id")
    private val emailColumn = varchar("email", Email.MaxLength)

    override val primaryKey = PrimaryKey(emailColumn)

    suspend fun insert(ownerId: UserId, email: Email) {
        insert { statement ->
            statement[ownerIdColumn] = ownerId.long
            statement[emailColumn] = email.string
        }
    }

    suspend fun select(email: Email): UserId? = selectAll()
        .where { emailColumn eq email.string }
        .map { row -> UserId(row[ownerIdColumn]) }
        .firstOrNull()

    suspend fun select(ids: List<UserId>): List<Email?> {
        val rawIds = ids.map(UserId::long)
        val results = selectAll()
            .where { ownerIdColumn inList rawIds }
            .toList()
            .associate { row ->
                UserId(row[ownerIdColumn]) to Email.orThrow(row[emailColumn])
            }
        return ids.map { id -> results[id] }
    }

    suspend fun delete(ownerId: UserId) {
        deleteWhere { ownerIdColumn eq ownerId.long }
    }
}
