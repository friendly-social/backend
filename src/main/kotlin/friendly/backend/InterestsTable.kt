package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object InterestsTable : Table("interests") {
    private val userIdColumn = long("user_id")
    private val nameColumn = varchar("name", Interest.MaxLength)

    override val primaryKey = PrimaryKey(nameColumn, userIdColumn)

    suspend fun impureInsert(userId: UserId, interests: List<Interest>) {
        batchInsert(interests) { (name) ->
            this[userIdColumn] = userId.long
            this[nameColumn] = name
        }
    }

    suspend fun impureSelect(userId: UserId): List<Interest> = selectAll()
        .where(userIdColumn eq userId.long)
        .map { result ->
            Interest.orThrow(result[nameColumn])
        }
        .toList()
}
