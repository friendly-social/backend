package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object TokenService {
    suspend fun generateIn(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
    ): GenerateResult {
        val token = Token.random()
        return suspendTransaction(context.database) {
            val userId = UserService.createIn(
                context = context,
                nickname = nickname,
                description = description,
                interests = interests,
            )
            TokensTable.insert(token, userId)
            GenerateResult(token, userId)
        }
    }

    data class GenerateResult(val token: Token, val userId: UserId)
}
