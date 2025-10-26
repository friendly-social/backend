package friendly.backend

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.batchInsert

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
}
