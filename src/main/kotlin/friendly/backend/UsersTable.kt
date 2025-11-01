package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object UsersTable : Table("users") {
    private val idColumn = long("id").autoIncrement()
    private val accessHashColumn = varchar("access_hash", UserAccessHash.Length)
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
            statement[accessHashColumn] = accessHash.string
        }
        return UserId(result[idColumn])
    }

    suspend fun impureSelect(id: UserId): Entry? {
        val result = selectAll()
            .where(idColumn eq id.long)
            .firstOrNull() ?: return null
        return Entry(
            id = UserId(result[idColumn]),
            accessHash = UserAccessHash.orThrow(result[accessHashColumn]),
            nickname = Nickname.orThrow(result[nicknameColumn]),
            description = UserDescription.orThrow(result[descriptionColumn]),
        )
    }

    data class Entry(
        val id: UserId,
        val accessHash: UserAccessHash,
        val nickname: Nickname,
        val description: UserDescription,
    )
}
