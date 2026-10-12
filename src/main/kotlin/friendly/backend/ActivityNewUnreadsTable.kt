package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.upsert

object ActivityNewUnreadsTable : Table("activity_new_unreads") {
    val userIdColumn = long("user_id")
    val newUnreadIdColumn = long("new_unread_id")

    override val primaryKey = PrimaryKey(userIdColumn)

    suspend fun select(userId: UserId): ActivityId? = select(newUnreadIdColumn)
        .where { userIdColumn eq userId.long }
        .firstOrNull()
        ?.get(newUnreadIdColumn)
        ?.let(::ActivityId)

    suspend fun upsert(userId: UserId, activityId: ActivityId) {
        upsert { statement ->
            statement[userIdColumn] = userId.long
            statement[newUnreadIdColumn] = activityId.long
        }
    }

    suspend fun delete(userId: UserId) {
        deleteWhere { userIdColumn eq userId.long }
    }

    suspend fun delete(userId: UserId, activityId: ActivityId) {
        deleteWhere {
            (userIdColumn eq userId.long) and
                (newUnreadIdColumn eq activityId.long)
        }
    }
}
