package friendly.backend.users

import friendly.backend.Nickname
import friendly.backend.UserId
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert

object UsersStorage : Table("users") {
    private val idColumn = long("id").autoIncrement()
    private val nicknameColumn = varchar("nickname", Nickname.MaxLength)

    suspend fun insert(nickname: Nickname): UserId {
        val result = insert { statement ->
            statement[nicknameColumn] = nickname.string
        }
        return UserId(result[idColumn])
    }
}
