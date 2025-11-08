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
        val id = authorization.id
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

    sealed interface AddResult {
        data object Unauthorized : AddResult
        data object FriendTokenExpired : AddResult
        data object Success : AddResult
    }

    suspend fun impureAdd(
        context: AppContext,
        authorization: Authorization,
        token: FriendToken,
        userId: UserId,
    ): AddResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return AddResult.Unauthorized }
        return suspendTransaction(context.database) {
            val isTokenValid = FriendTokensTable.impureExists(userId, token)
            if (isTokenValid) {
                FriendTokensTable.impureDelete(userId)

                val straightRelationExists = FriendsTable.impureExists(
                    fromId = authorization.id,
                    toId = userId,
                )
                if (!straightRelationExists) {
                    FriendsTable.impureInsert(
                        fromId = authorization.id,
                        toId = userId,
                    )
                }
                val reversedRelationExists = FriendsTable.impureExists(
                    fromId = userId,
                    toId = authorization.id,
                )
                if (!reversedRelationExists) {
                    FriendsTable.impureInsert(
                        fromId = userId,
                        toId = authorization.id,
                    )
                }
                AddResult.Success
            } else {
                AddResult.FriendTokenExpired
            }
        }
    }

    suspend fun impureList(
        context: AppContext,
        fromId: UserId,
    ): List<UserDetails> = suspendTransaction(context.database) {
        val friendIds = FriendsTable.impureSelect(fromId)
        val friendDetails = UsersService.impureDetails(context, friendIds)
            .map { details ->
                details ?: error(
                    "User not found, but it is unexpected since all friend ids must be existing users",
                )
            }
        friendDetails
    }
}
