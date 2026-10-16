package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

object ActivityTable : Table("activity") {
    private val idColumn = long("id").autoIncrement()
    private val typeColumn = enumeration<Type>("type")
    private val toIdColumn = long("to_id")
    private val instantColumn = timestamp("instant")
    private val isReadColumn = bool("is_read")

    private val replyPostIdColumn =
        long("reply_post_id").nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(payload: ActivityPayload): ActivityId =
        insert { statement ->
            statement[toIdColumn] = payload.toId.long
            statement[instantColumn] = payload.instant
            when (payload) {
                is Reply -> {
                    statement[typeColumn] = Type.Reply
                    statement[replyPostIdColumn] = payload.postId.long
                }
            }
        }.resultedValues!![0][idColumn].let(::ActivityId)

    data class SelectResult(
        val entries: List<ActivityEntry>,
        val hasNext: Boolean,
    )

    // fixme: not pure table logic, should be moved to service
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
            .map { row -> row.toEntry() }
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

    suspend fun selectLastUnreadId(toId: UserId): ActivityId? = select(idColumn)
        .where { (toIdColumn eq toId.long) and (isReadColumn eq false) }
        .orderBy(idColumn, DESC)
        .firstOrNull()
        ?.get(idColumn)
        ?.let(::ActivityId)

    suspend fun deleteReplies(postId: CommunityPostId) {
        deleteWhere {
            replyPostIdColumn eq postId.long
        }
    }

    suspend fun markRepliesAsRead(
        toId: UserId,
        postIds: List<CommunityPostId>,
    ) {
        val rawIds = postIds.map { id -> id.long }
        update(
            where = {
                (toIdColumn eq toId.long) and (replyPostIdColumn inList rawIds)
            },
        ) { statement ->
            statement[isReadColumn] = true
        }
    }

    suspend fun selectById(ids: List<ActivityId>): List<ActivityEntry?> {
        val rawIds = ids.map { id -> id.long }
        val map = selectAll()
            .where { idColumn inList rawIds }
            .map { row -> row.toEntry() }
            .toList()
            .associateBy { entry -> entry.id }
        return ids.map { id -> map[id] }
    }

    suspend fun markAsRead(id: ActivityId) {
        update({ idColumn eq id.long }) { statement ->
            statement[isReadColumn] = true
        }
    }

    private fun ResultRow.toEntry(): ActivityEntry {
        val id = ActivityId(this[idColumn])
        val toId = UserId(this[toIdColumn])
        val instant = this[instantColumn]
        val isRead = this[isReadColumn]
        return when (this[typeColumn]) {
            Reply -> {
                val postId = this[replyPostIdColumn]
                    ?.let(::CommunityPostId)
                    ?: error("Invalid ResultRow")
                ActivityEntry.Reply(id, toId, instant, isRead, postId)
            }
        }
    }

    enum class Type {
        Reply,
    }
}
