package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object InterestsTable : Table("interests") {
    private val userIdColumn = long("user_id")
    private val nameColumn = varchar("name", Interest.MaxLength)

    override val primaryKey = PrimaryKey(nameColumn, userIdColumn)

    suspend fun impureInsert(userId: UserId, interests: InterestList) {
        batchInsert(interests.raw) { (name) ->
            this[userIdColumn] = userId.long
            this[nameColumn] = name
        }
    }

    suspend fun impureSelect(userIds: List<UserId>): List<UserInterests> {
        val entries = selectAll()
            .where(userIdColumn inList userIds.map(UserId::long))
            .map { row ->
                Entry(
                    userId = UserId(row[userIdColumn]),
                    interest = Interest.orThrow(row[nameColumn]),
                )
            }
            .toList()
            .groupBy { entry -> entry.userId }

        return userIds.map { userId ->
            val entries = entries.getOrElse(userId) { emptyList() }
            val interestsRaw = entries.map(Entry::interest)
            val interests = InterestList.orThrow(interestsRaw)
            UserInterests(userId, interests)
        }
    }

    class Entry(val userId: UserId, val interest: Interest)
    class UserInterests(val userId: UserId, val list: InterestList)
}
