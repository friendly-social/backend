package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object FriendsService {

    sealed interface GenerateResult {
        data object Unauthorized : GenerateResult
        data class Success(val token: FriendToken) : GenerateResult
    }

    suspend fun impureGenerate(
        context: AppContext,
        authorization: Authorization,
    ): GenerateResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return GenerateResult.Unauthorized }
        val id = authorization.userId
        val token = FriendToken.impureRandom(context.random)
        return suspendTransaction(context.database) {
            suspend fun clearPreviousTokens() {
                FriendTokensTable.impureDelete(id)
            }
            clearPreviousTokens()
            FriendTokensTable.impureInsert(token, id)
            GenerateResult.Success(token)
        }
    }
}
