package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UserService {

    suspend fun createIn(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
    ): UserId = suspendTransaction(context.database) {
        val userId = UsersTable.insert(nickname, description)
        InterestsTable.insert(userId, interests)
        userId
    }
}
