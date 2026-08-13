package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object CommunityPostsPathTable : Table("community_posts_path") {
    private val postIdColumn = long("post_id")
    private val replyToColumn = long("reply_to")
    private val depthColumn = long("depth")

    override val primaryKey = PrimaryKey(postIdColumn, replyToColumn)

    suspend fun insert(postId: CommunityPostId, path: List<CommunityPostId>) {
        batchInsert(path.withIndex()) { (i, replyId) ->
            this[postIdColumn] = postId.long
            this[replyToColumn] = replyId.long
            this[depthColumn] = i + 1L
        }
    }

    suspend fun select(postId: CommunityPostId): List<CommunityPostId> =
        selectAll()
            .where { postIdColumn eq postId.long }
            .orderBy(depthColumn)
            .toList()
            .map { row -> CommunityPostId(row[replyToColumn]) }
}
