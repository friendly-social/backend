package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert
import kotlin.time.Duration
import kotlin.time.Instant

object FriendsTable : Table("friends") {
    val idColumn = long("id").autoIncrement()
    val fromIdColumn = long("from_id")
    val toIdColumn = long("to_id")
    val decisionColumn = enumeration<Decision>("decision")
    val declineTimesColumn = integer("decline_times")
    val declineForgetRandomColumn = duration("decline_forget_random")
    val instantColumn = timestamp("instant")

    override val primaryKey = PrimaryKey(fromIdColumn, toIdColumn)

    init {
        uniqueIndex(toIdColumn, fromIdColumn)
    }

    suspend fun upsert(
        fromId: UserId,
        toId: UserId,
        decision: Decision,
        declineTimes: Int,
        declineForgetRandom: Duration,
        instant: Instant,
    ) {
        upsert { statement ->
            statement[fromIdColumn] = fromId.long
            statement[toIdColumn] = toId.long
            statement[decisionColumn] = decision
            statement[declineTimesColumn] = declineTimes
            statement[declineForgetRandomColumn] = declineForgetRandom
            statement[instantColumn] = instant
        }
    }

    suspend fun select(descriptors: List<Descriptor>): List<Entry?> {
        val pairs = descriptors.map { (fromId, toId) ->
            fromId.long to toId.long
        }
        val results = selectAll()
            .where((fromIdColumn to toIdColumn) inList pairs)
            .map { row -> row.toEntry() }
            .toList()
            .associateBy(Entry::descriptor)
        return descriptors.map { descriptor -> results[descriptor] }
    }

    /**
     * Selects such users who [fromId] added as their friend.
     */
    suspend fun selectOutgoing(fromIds: List<UserId>): List<Entry> = selectAll()
        .where(fromIdColumn inList fromIds.map(UserId::long))
        .orderBy(idColumn, DESC)
        .map { row -> row.toEntry() }
        .toList()

    /**
     * Selects such users who added [fromId] as their friend.
     */
    suspend fun selectIncoming(toIds: List<UserId>): List<Entry> = selectAll()
        .where(toIdColumn inList toIds.map(UserId::long))
        .orderBy(idColumn, DESC)
        .map { row -> row.toEntry() }
        .toList()

    data class Descriptor(val fromId: UserId, val toId: UserId) {
        fun swap(): Descriptor = Descriptor(toId, fromId)
    }

    data class Entry(
        val id: Id,
        val fromId: UserId,
        val toId: UserId,
        val decision: Decision,
        val declineTimes: Int,
        val declineForgetRandom: Duration,
        val instant: Instant,
    ) {
        data class Id(val long: Long)

        val descriptor: Descriptor get() = Descriptor(fromId, toId)
    }

    enum class Decision {
        Request,
        Decline,
    }

    private fun ResultRow.toEntry(): Entry = Entry(
        id = Entry.Id(this[idColumn]),
        fromId = UserId(this[fromIdColumn]),
        toId = UserId(this[toIdColumn]),
        decision = this[decisionColumn],
        declineTimes = this[declineTimesColumn],
        declineForgetRandom = this[declineForgetRandomColumn],
        instant = this[instantColumn],
    )
}
