package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object TokensService {
    suspend fun generateIn(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
    ): GenerateResult {
        val token = Token.randomIn(context)
        return suspendTransaction(context.database) {
            val (id, accessHash) = UsersService.createIn(
                context = context,
                nickname = nickname,
                description = description,
                interests = interests,
            )
            TokensTable.insert(token, id)
            GenerateResult(token, id, accessHash)
        }
    }

    data class GenerateResult(
        val token: Token,
        val id: UserId,
        val accessHash: UserAccessHash,
    )
}
