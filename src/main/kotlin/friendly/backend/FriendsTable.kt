package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object FriendsTable : Table("friends") {
    val fromIdColumn = long("from_id")
    val toIdColumn = long("to_id")

    override val primaryKey = PrimaryKey(fromIdColumn, toIdColumn)

    suspend fun impureInsert(fromId: UserId, toId: UserId) {
        insert { statement ->
            statement[fromIdColumn] = fromId.long
            statement[toIdColumn] = toId.long
        }
    }

    suspend fun impureExists(entries: List<Entry>): List<Boolean> {
        val pairs = entries.map { (fromId, toId) -> fromId.long to toId.long }
        val results = selectAll()
            .where((fromIdColumn to toIdColumn) inList pairs)
            .map { row -> row.toEntry() }
            .toList()
            .toSet()
        return entries.map { entry -> entry in results }
    }

    /**
     * Selects such users who [fromId] added as their friend.
     */
    suspend fun impureSelectOutgoing(fromIds: List<UserId>): List<Entry> =
        selectAll()
            .where(fromIdColumn inList fromIds.map(UserId::long))
            .map { row -> row.toEntry() }
            .toList()

    data class Entry(val fromId: UserId, val toId: UserId)

    private fun ResultRow.toEntry(): Entry = Entry(
        fromId = UserId(this[fromIdColumn]),
        toId = UserId(this[toIdColumn]),
    )
}
