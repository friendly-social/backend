package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

object FriendsTable : Table("friends") {
    val fromIdColumn = long("from_id")
    val toIdColumn = long("to_id")
    val decisionColumn = enumeration<Decision>("decision")

    override val primaryKey = PrimaryKey(fromIdColumn, toIdColumn)

    suspend fun upsert(
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

    suspend fun select(descriptors: List<Descriptor>): List<Decision?> {
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

    /**
     * Selects such users who [fromId] added as their friend.
     */
    suspend fun selectOutgoing(fromIds: List<UserId>): List<Entry> =
        selectAll()
            .where(fromIdColumn inList fromIds.map(UserId::long))
            .map { row -> row.toEntry() }
            .toList()

    /**
     * Selects such users who added [fromId] as their friend.
     */
    suspend fun selectIncoming(toIds: List<UserId>): List<Entry> =
        selectAll()
            .where(toIdColumn inList toIds.map(UserId::long))
            .map { row -> row.toEntry() }
            .toList()

    data class Descriptor(val fromId: UserId, val toId: UserId) {
        fun swap(): Descriptor = Descriptor(toId, fromId)
    }

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

    private fun ResultRow.toEntry(): Entry = Entry(
        fromId = UserId(this[fromIdColumn]),
        toId = UserId(this[toIdColumn]),
        decision = this[decisionColumn],
    )
}
