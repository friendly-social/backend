package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UsersService {

    suspend fun impureCreate(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
    ): CreateResult = suspendTransaction(context.database) {
        val accessHash = UserAccessHash.impureRandom(context.random)
        val id = UsersTable.impureInsert(accessHash, nickname, description)
        InterestsTable.impureInsert(id, interests)
        CreateResult(id, accessHash)
    }

    data class CreateResult(val id: UserId, val accessHash: UserAccessHash)
}
