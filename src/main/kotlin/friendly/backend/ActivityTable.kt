package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.updateReturning

object ActivityTable : Table("activity") {
    private val idColumn = long("id").autoIncrement()
    private val typeColumn = enumeration<Type>("type")
    private val toIdColumn = long("to_id")
    private val instantColumn = timestamp("instant")
    private val isReadColumn = bool("is_read").default(false)

    private val replyPostIdColumn =
        long("reply_post_id").nullable()

    private val indirectRepliesPostIdColumn =
        long("indirect_replies_post_id").nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(payload: List<ActivityPayload>): List<ActivityId> =
        batchInsert(payload) { payload ->
            val statement = this
            statement[toIdColumn] = payload.toId.long
            statement[instantColumn] = payload.instant
            when (payload) {
                is Reply -> {
                    statement[typeColumn] = Type.Reply
                    statement[replyPostIdColumn] = payload.postId.long
                }
                is IndirectReplies -> {
                    statement[typeColumn] = Type.IndirectReplies
                    statement[indirectRepliesPostIdColumn] = payload.postId.long
                }
            }
        }.map { row ->
            row[idColumn].let(::ActivityId)
        }

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

    suspend fun selectUnreadReplies(
        postIds: List<CommunityPostId>,
    ): List<ActivityEntry.Reply> {
        val rawIds = postIds.map { id -> id.long }
        return selectAll()
            .where {
                (replyPostIdColumn inList rawIds) and (isReadColumn eq false)
            }
            .map { row -> row.toEntry() as Reply }
            .toList()
    }

    suspend fun selectUnreadIndirectReplies(
        toIds: List<UserId>,
        postId: CommunityPostId,
    ): Map<UserId, ActivityEntry.IndirectReplies> {
        val rawIds = toIds.map { id -> id.long }
        return selectAll()
            .where {
                (indirectRepliesPostIdColumn eq postId.long) and
                    (toIdColumn inList rawIds) and
                    (isReadColumn eq false)
            }
            .map { row -> row.toEntry() as IndirectReplies }
            .toList()
            .associateBy { entry -> entry.toId }
    }

    suspend fun deleteReplies(postId: CommunityPostId) {
        deleteWhere {
            replyPostIdColumn eq postId.long
        }
    }

    suspend fun deleteIndirectReplies(postId: CommunityPostId) {
        deleteWhere {
            indirectRepliesPostIdColumn eq postId.long
        }
    }

    suspend fun deleteIndirectRepliesByUserIds(
        toIds: List<UserId>,
        postId: CommunityPostId,
    ) {
        val rawIds = toIds.map { id -> id.long }
        deleteWhere {
            (toIdColumn inList rawIds) and
                (indirectRepliesPostIdColumn eq postId.long)
        }
    }

    suspend fun deleteIndirectRepliesWithoutUnreads(postId: CommunityPostId) {
        val noEntries = notExists(
            CommunityPostsPathTable
                .join(
                    CommunityUnreadsTable,
                    JoinType.INNER,
                    onColumn = CommunityPostsPathTable.postIdColumn,
                    otherColumn = CommunityUnreadsTable.postIdColumn,
                )
                .selectAll()
                .where {
                    (CommunityPostsPathTable.replyToColumn eq postId.long) and
                        (CommunityPostsPathTable.replyToDepthColumn eq 0) and
                        (CommunityUnreadsTable.userIdColumn eq toIdColumn)
                },
        )
        deleteWhere {
            (indirectRepliesPostIdColumn eq postId.long) and noEntries
        }
    }

    suspend fun markRepliesAsRead(
        toId: UserId,
        postIds: List<CommunityPostId>,
    ): List<ActivityId> {
        val rawIds = postIds.map { id -> id.long }
        return updateReturning(
            returning = listOf(idColumn),
            where = {
                (toIdColumn eq toId.long) and (replyPostIdColumn inList rawIds)
            },
        ) { statement ->
            statement[isReadColumn] = true
        }
            .map { row ->
                ActivityId(row[idColumn])
            }
            .toList()
    }

    suspend fun markIndirectRepliesAsRead(
        toId: UserId,
        postIds: List<CommunityPostId>,
    ): List<ActivityId> {
        val rawIds = postIds.map { id -> id.long }
        return updateReturning(
            returning = listOf(idColumn),
            where = {
                (toIdColumn eq toId.long) and
                    (indirectRepliesPostIdColumn inList rawIds)
            },
        ) { statement ->
            statement[isReadColumn] = true
        }
            .map { row ->
                ActivityId(row[idColumn])
            }
            .toList()
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
            IndirectReplies -> {
                val postId = this[indirectRepliesPostIdColumn]
                    ?.let(::CommunityPostId)
                    ?: error("Invalid ResultRow")
                ActivityEntry.IndirectReplies(
                    id = id,
                    toId = toId,
                    instant = instant,
                    isRead = isRead,
                    postId = postId,
                )
            }
        }
    }

    enum class Type {
        Reply,
        IndirectReplies,
    }
}
