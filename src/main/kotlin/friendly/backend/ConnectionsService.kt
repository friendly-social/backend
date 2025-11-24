package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object ConnectionsService {

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
        impureGetUser(context, userId, userAccessHash)
            ?: return RequestResult.NotFound
        return suspendTransaction(context.database) {
            ConnectionsTable.impureUpsert(
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
        impureGetUser(context, userId, userAccessHash)
            ?: return DeclineResult.NotFound
        return suspendTransaction(context.database) {
            ConnectionsTable.impureUpsert(
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
        id: UserId,
    ): List<UserDetails> = suspendTransaction(context.database) {
        val outgoingEntries = ConnectionsTable
            .impureSelectOutgoing(id)
        val outgoingDescriptors = outgoingEntries
            .filter { entry -> entry.decision == Request }
            .map { entry -> entry.descriptor }
        val incomingDescriptors = outgoingDescriptors
            .map { descriptor -> descriptor.swap() }
        val incomingDecisions = ConnectionsTable
            .impureSelect(incomingDescriptors)
            .iterator()
        val mutualConnections = outgoingDescriptors
            .filter { incomingDecisions.next() == Request }
            .map { descriptor -> descriptor.toId }
        val connectionDetails = UsersService.impureDetails(
            context = context,
            ids = mutualConnections,
        ).map { details ->
            details ?: error(
                "User not found, but it is unexpected since all friend ids must be existing users",
            )
        }
        connectionDetails
    }
}
