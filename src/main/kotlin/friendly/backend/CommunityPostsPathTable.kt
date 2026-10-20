package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll

/**
 * Linearizes graph traversal functions.
 *
 * Shows a relation of post <-> reply_to, where reply_to is not only a direct
 * reply_to, but also all the transitive ones.
 *
 * So, for a post with id 4 that is a direct reply to a post with id 3, the
 * following rows will be generated:
 *
 * | post_id | reply_to | post_depth | reply_to_depth |
 * | 4       | 3        | 4          | 3              |
 * | 4       | 2        | 4          | 2              |
 * | 4       | 1        | 4          | 1              |
 * | 4       | 0        | 4          | 0              |
 */
object CommunityPostsPathTable : Table("community_posts_path") {
    val postIdColumn = long("post_id")
    val replyToColumn = long("reply_to")
    val postDepthColumn = long("post_depth")
    val replyToDepthColumn = long("reply_to_depth")

    override val primaryKey = PrimaryKey(postIdColumn, replyToColumn)

    suspend fun insert(postId: CommunityPostId, path: List<CommunityPostId>) {
        batchInsert(path.withIndex()) { (i, replyId) ->
            this[postIdColumn] = postId.long
            this[replyToColumn] = replyId.long
            this[postDepthColumn] = path.size.toLong()
            this[replyToDepthColumn] = i.toLong()
        }
    }

    suspend fun deletePostsById(postId: CommunityPostId) {
        deleteWhere {
            (postIdColumn eq postId.long) or (replyToColumn eq postId.long)
        }
    }

    suspend fun selectRoot(postId: CommunityPostId): CommunityPostId? =
        select(replyToColumn)
            .where {
                (postIdColumn eq postId.long) and (replyToDepthColumn eq 0)
            }
            .firstOrNull()
            ?.get(replyToColumn)
            ?.let(::CommunityPostId)

    suspend fun selectRootBatch(
        postIds: List<CommunityPostId>,
    ): Map<CommunityPostId, CommunityPostId> {
        val rawIds = postIds.map { id -> id.long }
        return selectAll()
            .where {
                (postIdColumn inList rawIds) and (replyToDepthColumn eq 0)
            }
            .toList()
            .associate { row ->
                CommunityPostId(row[postIdColumn]) to
                    CommunityPostId(row[replyToColumn])
            }
    }

    suspend fun selectUpstream(postId: CommunityPostId): List<CommunityPostId> =
        selectAll()
            .where { postIdColumn eq postId.long }
            .orderBy(replyToDepthColumn)
            .toList()
            .map { row -> CommunityPostId(row[replyToColumn]) }

    suspend fun selectUpstreamBatch(
        postIds: List<CommunityPostId>,
    ): Map<CommunityPostId, List<CommunityPostId>> {
        val rawIds = postIds.map { id -> id.long }
        return selectAll()
            .where { postIdColumn inList rawIds }
            .orderBy(replyToDepthColumn)
            .toList()
            .groupBy(
                keySelector = { row -> CommunityPostId(row[postIdColumn]) },
                valueTransform = { row -> CommunityPostId(row[replyToColumn]) },
            )
            .withDefault { emptyList() }
    }

    suspend fun selectReplies(
        postIds: List<CommunityPostId>,
        maxDepth: Long,
        limit: Int,
    ): List<Entry> {
        val rawIds = postIds.map { id -> id.long }
        return selectAll()
            .where {
                (replyToColumn inList rawIds) and
                    ((postDepthColumn minus replyToDepthColumn) lessEq maxDepth)
            }
            .orderBy(postDepthColumn)
            .limit(limit)
            .toList()
            .map { row -> row.toEntry() }
    }

    private fun ResultRow.toEntry(): Entry = Entry(
        postId = CommunityPostId(this[postIdColumn]),
        replyTo = CommunityPostId(this[replyToColumn]),
        postDepth = this[postDepthColumn],
        replyToDepth = this[replyToDepthColumn],
    )

    data class Entry(
        val postId: CommunityPostId,
        val replyTo: CommunityPostId,
        val postDepth: Long,
        val replyToDepth: Long,
    )
}
