package friendly.backend

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.select

/**
 * Table for the actual blue dots that are shown to user. Subscriptions are
 * stored under [CommunityVisitsTable].
 */
object CommunityNewUnreadsTable : Table("community_new_unreads") {
    val userIdColumn = long("user_id")
    val postIdColumn = long("post_id")

    override val primaryKey = PrimaryKey(userIdColumn, postIdColumn)

    suspend fun upsert(postId: CommunityPostId, userIds: List<UserId>) {
        batchUpsert(userIds) { userId ->
            this[userIdColumn] = userId.long
            this[postIdColumn] = postId.long
        }
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
                    (userIdColumn eq userId.long)
            }
            .groupBy(CommunityPostsPathTable.replyToColumn)
            .toList()
            .associate { row ->
                CommunityPostId(row[CommunityPostsPathTable.replyToColumn]) to
                    CommunityPostId(row[postIdMax]!!)
            }
    }

    suspend fun delete(userId: UserId, postIds: List<CommunityPostId>) {
        val rawIds = postIds.map { id -> id.long }
        deleteWhere {
            (userIdColumn eq userId.long) and (postIdColumn inList rawIds)
        }
    }
}
