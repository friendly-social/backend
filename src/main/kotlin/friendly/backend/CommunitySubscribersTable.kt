package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.upsert

/**
 * Table for user "subscriptions" on posts. Comparing to
 * [CommunityViewersTable], this one is only used after user sent a reply
 * under a post or when they are a post author.
 */
object CommunitySubscribersTable : Table("community_subscribers") {
    val userIdColumn = long("user_id")
    val postIdColumn = long("post_id")

    override val primaryKey = PrimaryKey(userIdColumn, postIdColumn)

    suspend fun upsert(userId: UserId, postId: CommunityPostId) {
        upsert { statement ->
            statement[userIdColumn] = userId.long
            statement[postIdColumn] = postId.long
        }
    }

    suspend fun selectUsers(postId: CommunityPostId): List<UserId> =
        select(userIdColumn)
            .where { postIdColumn eq postId.long }
            .map { row -> UserId(row[userIdColumn]) }
            .toList()
}
