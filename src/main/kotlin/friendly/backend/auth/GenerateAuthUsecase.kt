package friendly.backend.auth

import friendly.backend.AppContext
import friendly.backend.Nickname
import friendly.backend.Token
import friendly.backend.UserId
import friendly.backend.users.UsersStorage
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun GenerateAuthUsecase(context: AppContext): GenerateAuthUsecase =
    GenerateAuthUsecase(context.db)

class GenerateAuthUsecase(private val db: R2dbcDatabase) {
    suspend operator fun invoke(nickname: Nickname): Result {
        val token = Token.random()
        return suspendTransaction(db) {
            val userId = UsersStorage.insert(nickname)
            TokensStorage.insert(token, userId)
            Result(token, userId)
        }
    }

    data class Result(val token: Token, val userId: UserId)
}
