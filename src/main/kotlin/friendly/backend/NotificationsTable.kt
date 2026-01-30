package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object NotificationsTable : Table("notifications") {
    private val idColumn = long("id").autoIncrement()
    private val type = enumeration<Type>("type")
    private val toIdColumn = long("to_id")

    private val newRequestFromIdColumn =
        long("new_request_from_id").nullable()
    private val newRequestIsMutualColumn =
        bool("new_request_is_mutual").nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun impureInsertNewRequest(
        toId: UserId,
        fromId: UserId,
        isMutual: Boolean,
    ): Entry = insert { statement ->
        statement[toIdColumn] = toId.long
        statement[type] = Type.NewRequest
        statement[newRequestFromIdColumn] = fromId.long
        statement[newRequestIsMutualColumn] = isMutual
    }.resultedValues!![0].toEntry()

    suspend fun impureSelect(): List<Entry> = selectAll()
        .map { row -> row.toEntry() }
        .toList()

    suspend fun impureDelete(id: NotificationId) {
        deleteWhere { idColumn eq id.long }
    }

    private fun ResultRow.toEntry(): Entry {
        val id = NotificationId(this[idColumn])
        val toId = UserId(this[toIdColumn])
        return when (this[type]) {
            NewRequest -> {
                val fromId = this[newRequestFromIdColumn]
                    ?.let(::UserId)
                    ?: error("Invalid ResultRow")
                val isMutual = this[newRequestIsMutualColumn]
                    ?: error("Invalid ResultRow")
                Entry.NewRequest(id, toId, fromId, isMutual)
            }
        }
    }

    sealed interface Entry {
        val id: NotificationId
        val toId: UserId
        val type: Type

        data class NewRequest(
            override val id: NotificationId,
            override val toId: UserId,
            val fromId: UserId,
            val isMutual: Boolean,
        ) : Entry {
            override val type: Type = Type.NewRequest
        }
    }

    enum class Type {
        NewRequest,
    }
}
