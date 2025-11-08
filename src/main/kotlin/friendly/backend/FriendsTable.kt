package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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

    suspend fun impureExists(fromId: UserId, toId: UserId): Boolean {
        val entry = selectAll()
            .where((fromIdColumn eq fromId.long) and (toIdColumn eq toId.long))
            .firstOrNull()
        return entry != null
    }

    suspend fun impureSelect(fromId: UserId): List<UserId> = selectAll()
        .where(fromIdColumn eq fromId.long)
        .map { statement -> UserId(statement[toIdColumn]) }
        .toList()
}
