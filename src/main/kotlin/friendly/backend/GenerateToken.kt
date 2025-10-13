package friendly.backend

import friendly.backend.AppContext
import friendly.backend.Nickname
import friendly.backend.Token
import friendly.backend.UserId
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

data class GenerateTokenResult(val token: Token, val userId: UserId)

suspend fun generateToken(
    context: AppContext,
    nickname: Nickname,
    description: UserDescription,
    interests: List<Interest>,
): GenerateTokenResult {
    val token = Token.random()
    return suspendTransaction(context.db) {
        val userId = createUser(context, nickname, description, interests)
        TokensTable.insert(token, userId)
        GenerateTokenResult(token, userId)
    }
}
