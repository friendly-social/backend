package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object ActivityTable : Table("activity") {
    private val idColumn = long("id").autoIncrement()
    private val typeColumn = enumeration<Type>("type")
    private val toIdColumn = long("to_id")
    private val instantColumn = timestamp("instant")

    private val replyPostIdColumn =
        long("reply_post_id").nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(payload: ActivityPayload) {
        insert { statement ->
            statement[toIdColumn] = payload.toId.long
            statement[instantColumn] = payload.instant
            when (payload) {
                is Reply -> {
                    statement[typeColumn] = Type.Reply
                    statement[replyPostIdColumn] = payload.postId.long
                }
            }
        }
    }

    data class SelectResult(
        val entries: List<ActivityEntry>,
        val hasNext: Boolean,
    )

    suspend fun select(
        toId: UserId,
        before: ActivityId?,
        limit: Int,
    ): SelectResult {
        val entries = selectAll()
            .where {
                (toIdColumn eq toId.long) and
                    (idColumn less (before?.long ?: MAX_VALUE))
            }
            .orderBy(idColumn, DESC)
            .limit(limit + 1)
            .map { row -> row.toRecord() }
            .toList()
        return if (entries.size == limit + 1) {
            SelectResult(
                entries = entries.dropLast(1),
                hasNext = true,
            )
        } else {
            SelectResult(
                entries = entries,
                hasNext = false,
            )
        }
    }

    private fun ResultRow.toRecord(): ActivityEntry {
        val id = ActivityId(this[idColumn])
        val toId = UserId(this[toIdColumn])
        val instant = this[instantColumn]
        return when (this[typeColumn]) {
            Reply -> {
                val postId = this[replyPostIdColumn]
                    ?.let(::CommunityPostId)
                    ?: error("Invalid ResultRow")
                ActivityEntry.Reply(id, toId, instant, postId)
            }
        }
    }

    enum class Type {
        Reply,
    }
}
