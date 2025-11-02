package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object AuthService {
    sealed interface AuthorizeResult {
        data object Failure : AuthorizeResult
        data object Success : AuthorizeResult
    }

    suspend fun impureAuthorize(
        context: AppContext,
        authorization: Authorization,
    ): AuthorizeResult {
        val token = authorization.token
        val userId = authorization.id
        return suspendTransaction(context.database) {
            val exists = TokensTable.impureExists(token, userId)
            if (exists) {
                AuthorizeResult.Success
            } else {
                AuthorizeResult.Failure
            }
        }
    }
}

inline fun AuthService.AuthorizeResult.onFailure(
    block: () -> Unit,
): AuthService.AuthorizeResult {
    if (this is AuthService.AuthorizeResult.Failure) block()
    return this
}
