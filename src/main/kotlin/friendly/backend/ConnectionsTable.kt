package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.upsert
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object ConnectionsTable : Table("connections") {
    val fromIdColumn = long("from_id")
    val toIdColumn = long("to_id")
    val decisionColumn = enumeration<Decision>("decision")

    override val primaryKey = PrimaryKey(fromIdColumn, toIdColumn)

    suspend fun impureUpsert(
        fromId: UserId,
        toId: UserId,
        decision: Decision,
    ) {
        upsert { statement ->
            statement[fromIdColumn] = fromId.long
            statement[toIdColumn] = toId.long
            statement[decisionColumn] = decision
        }
    }

    suspend fun impureExists(
        fromId: UserId,
        toId: UserId,
    ): Boolean {
        val first = selectAll()
            .where((fromIdColumn eq fromId.long) and (toIdColumn eq toId.long))
            .firstOrNull()
        return first != null
    }

    suspend fun impureSelect(fromId: UserId, toId: UserId): Decision? {
        return impureSelect(listOf(Descriptor(fromId, toId))).first()
    }

    suspend fun impureSelect(descriptors: List<Descriptor>): List<Decision?> {
        val pairs = descriptors.map { (fromId, toId) ->
            fromId.long to toId.long
        }
        val results = selectAll()
            .where((fromIdColumn to toIdColumn) inList pairs)
            .map { row -> row.toEntry() }
            .toList()
            .associateBy(Entry::descriptor)
        return descriptors.map { descriptor -> results[descriptor]?.decision }
    }

    data class Descriptor(
        val fromId: UserId,
        val toId: UserId,
    )

    data class Entry(
        val fromId: UserId,
        val toId: UserId,
        val decision: Decision,
    ) {
        val descriptor: Descriptor get() = Descriptor(fromId, toId)
    }

    enum class Decision {
        Request,
        Decline,
    }

    private fun ResultRow.toEntry(): Entry {
        return Entry(
            fromId = UserId(this[fromIdColumn]),
            toId = UserId(this[toIdColumn]),
            decision = this[decisionColumn],
        )
    }
}
