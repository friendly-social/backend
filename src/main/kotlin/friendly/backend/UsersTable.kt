package friendly.backend

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert

object UsersTable : Table("users") {
    private val idColumn = long("id").autoIncrement()
    private val accessHash = varchar("access_hash", UserAccessHash.Length)
    private val nicknameColumn = varchar("nickname", Nickname.MaxLength)

    private val descriptionColumn =
        varchar("description", UserDescription.MaxLength)

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun impureInsert(
        accessHash: UserAccessHash,
        nickname: Nickname,
        description: UserDescription,
    ): UserId {
        val result = insert { statement ->
            statement[nicknameColumn] = nickname.string
            statement[descriptionColumn] = description.string
        }
        return UserId(result[idColumn])
    }
}
