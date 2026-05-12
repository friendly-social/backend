package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object AuthService {
    sealed interface AuthorizeResult {
        data object Failure : AuthorizeResult
        data object Success : AuthorizeResult
    }

    suspend fun authorize(
        context: AppContext,
        authorization: Authorization,
    ): AuthorizeResult {
        val token = authorization.token
        val userId = authorization.id
        return suspendTransaction(context.database) {
            val exists = TokensTable.exists(token, userId)
            if (exists) {
                AuthorizeResult.Success
            } else {
                AuthorizeResult.Failure
            }
        }
    }

    sealed interface EmailResult {
        data object UnknownEmail : EmailResult
        data object Success : EmailResult
    }

    suspend fun email(
        context: AppContext,
        email: Email,
        localeCode: LocaleCode,
    ): EmailResult {
        return suspendTransaction(context.database) {
            val ownerId = EmailsTable.select(email)
            if (ownerId == null) {
                return@suspendTransaction UnknownEmail
            }
            val previousEntry = LoginCodesTable.select(email)
            if (previousEntry != null) {
                val now = context.clock.now()
                val isCodeActive = now < previousEntry.expiration.instant
                if (isCodeActive) {
                    return@suspendTransaction Success
                }
                LoginCodesTable.delete(email)
            }
            val now = context.clock.now()
            val loginCode = LoginCode.random(context.random)
            val expiration = LoginCodeExpiration.createdNow(now)
            LoginCodesTable.insert(email, loginCode, expiration)
            AuthMailService.send(context, email, localeCode, loginCode)
            Success
        }
    }

    sealed interface LoginResult {
        data object InvalidCode : LoginResult
        data class Success(
            val token: Token,
            val id: UserId,
            val accessHash: UserAccessHash,
        ) : LoginResult
    }

    suspend fun login(
        context: AppContext,
        email: Email,
        code: LoginCode,
    ): LoginResult {
        return suspendTransaction(context.database) {
            val ownerId = EmailsTable.select(email)
                ?: return@suspendTransaction LoginResult.InvalidCode
            val entry = LoginCodesTable.select(email)
                ?: return@suspendTransaction LoginResult.InvalidCode
            val now = context.clock.now()
            if (now > entry.expiration.instant) {
                return@suspendTransaction LoginResult.InvalidCode
            }
            if (entry.attempts.int == LoginCodeAttempts.Max) {
                return@suspendTransaction LoginResult.InvalidCode
            }
            if (entry.code != code) {
                LoginCodesTable.updateAttempts(
                    email = email,
                    attempts = entry.attempts.incrementOrThrow(),
                )
                return@suspendTransaction LoginResult.InvalidCode
            }
            LoginCodesTable.delete(email)
            val token = TokensService.add(context, ownerId)
            val ids = listOf(ownerId)
            val details = UsersService.details(context, ownerId, ids).first()
            checkNotNull(details) {
                "User must exist if userId was found in emails"
            }
            LoginResult.Success(token, ownerId, details.accessHash)
        }
    }
}

inline fun AuthService.AuthorizeResult.onFailure(
    block: () -> Unit,
): AuthService.AuthorizeResult {
    if (this is AuthService.AuthorizeResult.Failure) block()
    return this
}
