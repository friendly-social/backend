package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object EmailService {
    sealed interface LinkResult {
        data object Unauthorized : LinkResult
        data object EmailAlreadyUsed : LinkResult
        data object Success : LinkResult
    }

    suspend fun link(
        context: AppContext,
        authorization: Authorization,
        email: Email,
        localeCode: LocaleCode,
    ): LinkResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            val emailOwnerId = EmailsTable.select(email)
            if (emailOwnerId != null) {
                if (emailOwnerId != authorization.id) {
                    return@suspendTransaction EmailAlreadyUsed
                } else {
                    return@suspendTransaction Success
                }
            }
            val previousEntry = ConfirmationCodesTable.select(email)
            if (previousEntry != null) {
                val now = context.clock.now()
                val isCodeActive = now < previousEntry.expiration.instant
                if (isCodeActive) {
                    if (previousEntry.ownerId == authorization.id) {
                        return@suspendTransaction Success
                    } else {
                        return@suspendTransaction EmailAlreadyUsed
                    }
                }
                ConfirmationCodesTable.delete(previousEntry.ownerId)
            }
            val ownerId = authorization.id
            ConfirmationCodesTable.delete(ownerId)
            val code = ConfirmationCode.random(context.random)
            val now = context.clock.now()
            val expiration = ConfirmationCodeExpiration.createdNow(now)
            ConfirmationCodesTable.insert(ownerId, email, code, expiration)
            ConfirmationMailService.send(context, email, localeCode, code)
            Success
        }
    }

    sealed interface ConfirmResult {
        data object Unauthorized : ConfirmResult
        data object InvalidCode : ConfirmResult
        data object Success : ConfirmResult
    }

    suspend fun confirm(
        context: AppContext,
        authorization: Authorization,
        code: ConfirmationCode,
    ): ConfirmResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            val entry = ConfirmationCodesTable.select(authorization.id)
            if (entry == null) {
                return@suspendTransaction InvalidCode
            }
            val now = context.clock.now()
            if (now > entry.expiration.instant) {
                return@suspendTransaction InvalidCode
            }
            if (entry.attempts.int == ConfirmationCodeAttempts.Max) {
                return@suspendTransaction InvalidCode
            }
            if (entry.code != code) {
                ConfirmationCodesTable.updateAttempts(
                    ownerId = authorization.id,
                    attempts = entry.attempts.incrementOrThrow(),
                )
                return@suspendTransaction InvalidCode
            }
            ConfirmationCodesTable.delete(authorization.id)
            EmailsTable.delete(authorization.id)
            EmailsTable.insert(
                ownerId = authorization.id,
                email = entry.email,
            )
            Success
        }
    }

    sealed interface UnlinkResult {
        data object Unauthorized : UnlinkResult
        data object Success : UnlinkResult
    }

    suspend fun unlink(
        context: AppContext,
        authorization: Authorization,
    ): UnlinkResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            EmailsTable.delete(authorization.id)
            Success
        }
    }
}
