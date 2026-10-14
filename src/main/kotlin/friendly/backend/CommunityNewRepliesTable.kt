package friendly.backend

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll

/**
 * Table for the actual blue dots that are shown to user. Subscriptions are
 * stored under [CommunityVisitsTable].
 *
 * This table doesn't store transitive replies, hence they should be selected
 * manually.
 */
object CommunityNewRepliesTable : Table("community_new_replies") {
    val userIdColumn = long("user_id")
    val postIdColumn = long("post_id")
    val newReplyColumn = long("new_reply")

    override val primaryKey = PrimaryKey(userIdColumn, postIdColumn)

    suspend fun upsertReplies(
        postId: CommunityPostId,
        newReply: CommunityPostId,
        userIds: List<UserId>,
    ) {
        batchUpsert(userIds) { userId ->
            this[userIdColumn] = userId.long
            this[postIdColumn] = postId.long
            this[newReplyColumn] = newReply.long
        }
    }

    suspend fun selectByUserId(
        userId: UserId,
        postIds: List<CommunityPostId>,
    ): Map<CommunityPostId, CommunityPostId?> {
        val rawIds = postIds.map { id -> id.long }
        return selectAll()
            .where {
                (userIdColumn eq userId.long) and (postIdColumn inList rawIds)
            }
            .toList()
            .associate { entry ->
                val postId = CommunityPostId(entry[postIdColumn])
                val replyId = CommunityPostId(entry[newReplyColumn])
                postId to replyId
            }
            .withDefault { null }
    }

    suspend fun delete(userId: UserId, postId: CommunityPostId) {
        deleteWhere {
            (userIdColumn eq userId.long) and (postIdColumn eq postId.long)
        }
    }
}
