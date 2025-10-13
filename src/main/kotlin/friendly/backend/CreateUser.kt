package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspend fun createUser(
    context: AppContext,
    nickname: Nickname,
    description: UserDescription,
    interests: List<Interest>,
): UserId =
    suspendTransaction(context.db) {
        val userId = UsersTable.insert(nickname, description)
        InterestsTable.insert(userId, interests)
        userId
    }
