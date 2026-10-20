package friendly.backend

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.update

/**
 * Table for the actual blue dots that are shown to user. Subscriptions are
 * stored under [CommunityViewersTable].
 */
object CommunityUnreadsTable : Table("community_unreads") {
    val userIdColumn = long("user_id")
    val postIdColumn = long("post_id")
    val isReadColumn = bool("is_read").default(false)

    override val primaryKey = PrimaryKey(userIdColumn, postIdColumn)

    suspend fun upsert(postId: CommunityPostId, userIds: List<UserId>) {
        batchUpsert(userIds) { userId ->
            this[userIdColumn] = userId.long
            this[postIdColumn] = postId.long
        }
    }

    suspend fun selectTransitiveGroupedUnreads(
        userId: UserId,
        postIds: List<CommunityPostId>,
        limit: Int,
    ): Map<CommunityPostId, List<CommunityPostId>> {
        if (postIds.isEmpty() || limit <= 0) return emptyMap()
        val rawIds = postIds.map { id -> id.long }

        val rowNumber = rowNumber()
            .over()
            .partitionBy(CommunityPostsPathTable.replyToColumn)
            .orderBy(postIdColumn, DESC)
            .alias("row_number")

        val ranked = join(
            CommunityPostsPathTable,
            JoinType.INNER,
            onColumn = postIdColumn,
            otherColumn = CommunityPostsPathTable.postIdColumn,
        )
            .select(
                CommunityPostsPathTable.replyToColumn,
                postIdColumn,
                rowNumber,
            )
            .where {
                (CommunityPostsPathTable.replyToColumn inList rawIds) and
                    (userIdColumn eq userId.long) and
                    (isReadColumn eq false)
            }
            .alias("ranked")

        return ranked.select(
            ranked[CommunityPostsPathTable.replyToColumn],
            ranked[postIdColumn],
        )
            .where { ranked[rowNumber] lessEq limit.toLong() }
            .toList()
            .groupBy(
                keySelector = { row ->
                    val column = ranked[CommunityPostsPathTable.replyToColumn]
                    CommunityPostId(row[column])
                },
                valueTransform = { row ->
                    val column = ranked[postIdColumn]
                    CommunityPostId(row[column])
                },
            )
    }

    suspend fun selectTransitiveNewUnreads(
        userId: UserId,
        postIds: List<CommunityPostId>,
    ): Map<CommunityPostId, CommunityPostId> {
        val postIdMax = postIdColumn.max()
        val rawIds = postIds.map { id -> id.long }
        return join(
            CommunityPostsPathTable,
            JoinType.INNER,
            onColumn = postIdColumn,
            otherColumn = CommunityPostsPathTable.postIdColumn,
        )
            .select(CommunityPostsPathTable.replyToColumn, postIdMax)
            .where {
                (CommunityPostsPathTable.replyToColumn inList rawIds) and
                    (userIdColumn eq userId.long) and
                    (isReadColumn eq false)
            }
            .groupBy(CommunityPostsPathTable.replyToColumn)
            .toList()
            .associate { row ->
                CommunityPostId(row[CommunityPostsPathTable.replyToColumn]) to
                    CommunityPostId(row[postIdMax]!!)
            }
    }

    suspend fun markAsRead(userId: UserId, postIds: List<CommunityPostId>) {
        val rawIds = postIds.map { id -> id.long }
        update(
            where = {
                (userIdColumn eq userId.long) and (postIdColumn inList rawIds)
            },
        ) { statement ->
            statement[isReadColumn] = true
        }
    }

    suspend fun delete(postId: CommunityPostId) {
        deleteWhere {
            postIdColumn eq postId.long
        }
    }
}
