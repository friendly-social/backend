package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UsersService {

    suspend fun createIn(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
    ): CreateResult = suspendTransaction(context.database) {
        val accessHash = UserAccessHash.randomIn(context)
        val id = UsersTable.insert(accessHash, nickname, description)
        InterestsTable.insert(id, interests)
        CreateResult(id, accessHash)
    }

    data class CreateResult(val id: UserId, val accessHash: UserAccessHash)
}
