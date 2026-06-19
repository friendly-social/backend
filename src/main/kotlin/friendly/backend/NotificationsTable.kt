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
    private val typeColumn = enumeration<Type>("type")
    private val toIdColumn = long("to_id")

    private val newRequestFromIdColumn =
        long("new_request_from_id").nullable()
    private val newRequestIsMutualColumn =
        bool("new_request_is_mutual").nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(payload: NotificationPayload): NotificationRecord =
        insert { statement ->
            statement[toIdColumn] = payload.toId.long
            when (payload) {
                is NewRequest -> {
                    statement[typeColumn] = Type.NewRequest
                    statement[newRequestFromIdColumn] = payload.fromId.long
                    statement[newRequestIsMutualColumn] = payload.isMutual
                }
            }
        }.resultedValues!![0].toRecord()

    suspend fun select(): List<NotificationRecord> = selectAll()
        .map { row -> row.toRecord() }
        .toList()

    suspend fun delete(id: NotificationId) {
        deleteWhere { idColumn eq id.long }
    }

    private fun ResultRow.toRecord(): NotificationRecord {
        val id = NotificationId(this[idColumn])
        val toId = UserId(this[toIdColumn])
        return when (this[typeColumn]) {
            NewRequest -> {
                val fromId = this[newRequestFromIdColumn]
                    ?.let(::UserId)
                    ?: error("Invalid ResultRow")
                val isMutual = this[newRequestIsMutualColumn]
                    ?: error("Invalid ResultRow")
                NotificationRecord.NewRequest(id, toId, fromId, isMutual)
            }
        }
    }

    enum class Type {
        NewRequest,
    }
}
