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
        if (userId == authorization.id) {
            return Success
        }
        return suspendTransaction(context.database) {
            val isTokenValid = FriendTokensTable.impureExists(userId, token)
            if (isTokenValid) {
                FriendTokensTable.impureDelete(userId)
                FriendsTable.impureUpsert(
                    fromId = authorization.id,
                    toId = userId,
                    decision = Request,
                )
                FriendsTable.impureUpsert(
                    fromId = userId,
                    toId = authorization.id,
                    decision = Request,
                )
                AddResult.Success
            } else {
                AddResult.FriendTokenExpired
            }
        }
    }

    sealed interface RequestResult {
        data object Unauthorized : RequestResult
        data object NotFound : RequestResult
        data object Success : RequestResult
    }

    suspend fun impureRequest(
        context: AppContext,
        authorization: Authorization,
        userId: UserId,
        userAccessHash: UserAccessHash,
    ): RequestResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return RequestResult.Unauthorized }
        if (userId == authorization.id) {
            return Success
        }
        impureGetUser(context, userId, userAccessHash)
            ?: return RequestResult.NotFound
        return suspendTransaction(context.database) {
            FriendsTable.impureUpsert(
                fromId = authorization.id,
                toId = userId,
                decision = Request,
            )
            RequestResult.Success
        }
    }

    sealed interface DeclineResult {
        data object Unauthorized : DeclineResult
        data object NotFound : DeclineResult
        data object Success : DeclineResult
    }

    suspend fun impureDecline(
        context: AppContext,
        authorization: Authorization,
        userId: UserId,
        userAccessHash: UserAccessHash,
    ): DeclineResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return DeclineResult.Unauthorized }
        if (userId == authorization.id) {
            return Success
        }
        impureGetUser(context, userId, userAccessHash)
            ?: return DeclineResult.NotFound
        return suspendTransaction(context.database) {
            FriendsTable.impureUpsert(
                fromId = authorization.id,
                toId = userId,
                decision = Decline,
            )
            DeclineResult.Success
        }
    }

    private suspend fun impureGetUser(
        context: AppContext,
        id: UserId,
        accessHash: UserAccessHash,
    ): UserDetails? {
        val user = UsersService.impureDetails(context, listOf(id)).first()
            ?: return null
        if (accessHash != user.accessHash) {
            return null
        }
        return user
    }

    suspend fun impureList(
        context: AppContext,
        fromId: UserId,
    ): List<UserDetails> = suspendTransaction(context.database) {
        val outgoingEntries = FriendsTable
            .impureSelectOutgoing(listOf(fromId))
        val outgoingDescriptors = outgoingEntries
            .filter { entry -> entry.decision == Request }
            .map { entry -> entry.descriptor }
        val incomingDescriptors = outgoingDescriptors
            .map { descriptor -> descriptor.swap() }
        val incomingDecisions = FriendsTable
            .impureSelect(incomingDescriptors)
            .iterator()
        val mutualFriends = outgoingDescriptors
            .filter { incomingDecisions.next() == Request }
            .map { descriptor -> descriptor.toId }
        val friendDetails = UsersService.impureDetails(
            context = context,
            ids = mutualFriends,
        ).map { details ->
            details ?: error(
                "User not found, but it is unexpected since all friend ids must be existing users",
            )
        }
        friendDetails
    }
}
