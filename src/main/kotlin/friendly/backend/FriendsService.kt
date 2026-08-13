package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object FriendsService {

    sealed interface GenerateResult {
        data object Unauthorized : GenerateResult
        data class Success(val token: FriendToken) : GenerateResult
    }

    suspend fun generate(
        context: AppContext,
        authorization: Authorization,
    ): GenerateResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return GenerateResult.Unauthorized }
        return suspendTransaction(context.database) {
            val previousToken = FriendTokensTable.select(authorization.id)
            val token = if (previousToken == null) {
                generateForce(context, authorization.id)
            } else {
                previousToken
            }
            GenerateResult.Success(token)
        }
    }

    suspend fun generateForce(
        context: AppContext,
        authorization: Authorization,
    ): GenerateResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return GenerateResult.Unauthorized }
        return suspendTransaction(context.database) {
            val token = generateForce(context, authorization.id)
            GenerateResult.Success(token)
        }
    }

    suspend fun generateForce(
        context: AppContext,
        userId: UserId,
    ): FriendToken = suspendTransaction(context.database) {
        FriendTokensTable.delete(userId)
        FriendToken.random(context.random).also { token ->
            FriendTokensTable.insert(token, userId)
        }
    }

    sealed interface AddResult {
        data object Unauthorized : AddResult
        data object FriendTokenExpired : AddResult
        data object Success : AddResult
    }

    suspend fun add(
        context: AppContext,
        authorization: Authorization,
        token: FriendToken,
        userId: UserId,
    ): AddResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return AddResult.Unauthorized }
        if (userId == authorization.id) {
            return Success
        }
        if (addViaPermanentLink(context, authorization, token, userId)) {
            return Success
        }
        return suspendTransaction(context.database) {
            val isTokenValid = FriendTokensTable.exists(userId, token)
            if (isTokenValid) {
                FriendTokensTable.delete(userId)
                FriendsTable.upsert(
                    fromId = authorization.id,
                    toId = userId,
                    decision = Request,
                )
                FriendsTable.upsert(
                    fromId = userId,
                    toId = authorization.id,
                    decision = Request,
                )
                Success
            } else {
                FriendTokenExpired
            }
        }
    }

    suspend fun addViaPermanentLink(
        context: AppContext,
        authorization: Authorization,
        token: FriendToken,
        userId: UserId,
    ): Boolean {
        val linkExists = PermanentLinks.Entries
            .any { (permanentToken, permanentUserId) ->
                permanentToken == token && permanentUserId == userId
            }
        if (!linkExists) {
            return false
        }
        suspendTransaction(context.database) {
            FriendsTable.upsert(
                fromId = authorization.id,
                toId = userId,
                decision = Request,
            )
            FriendsTable.upsert(
                fromId = userId,
                toId = authorization.id,
                decision = Request,
            )
        }
        return true
    }

    sealed interface RequestResult {
        data object Unauthorized : RequestResult
        data object NotFound : RequestResult
        data object Success : RequestResult
    }

    suspend fun request(
        context: AppContext,
        authorization: Authorization,
        userId: UserId,
        userAccessHash: UserAccessHash,
    ): RequestResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return RequestResult.Unauthorized }
        if (userId == authorization.id) {
            return Success
        }
        getUser(context, authorization, userId, userAccessHash)
            ?: return RequestResult.NotFound
        val descriptor = FriendsTable.Descriptor(authorization.id, userId)
        val (outgoing, incoming) = suspendTransaction(context.database) {
            FriendsTable.select(
                descriptors = listOf(
                    descriptor,
                    descriptor.swap(),
                ),
            )
        }
        val shouldSendNotification = outgoing == null
        if (shouldSendNotification) {
            if (incoming != Decline) {
                val isMutual = incoming == Request
                val notification = NotificationPayload.NewRequest(
                    toId = userId,
                    fromId = authorization.id,
                    isMutual = isMutual,
                )
                NotificationsService.schedule(context, notification)
            }
        }
        return suspendTransaction(context.database) {
            FriendsTable.upsert(
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

    suspend fun decline(
        context: AppContext,
        authorization: Authorization,
        userId: UserId,
        userAccessHash: UserAccessHash,
    ): DeclineResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return DeclineResult.Unauthorized }
        if (userId == authorization.id) {
            return Success
        }
        getUser(context, authorization, userId, userAccessHash)
            ?: return DeclineResult.NotFound
        return suspendTransaction(context.database) {
            FriendsTable.upsert(
                fromId = authorization.id,
                toId = userId,
                decision = Decline,
            )
            DeclineResult.Success
        }
    }

    private suspend fun getUser(
        context: AppContext,
        authorization: Authorization,
        id: UserId,
        accessHash: UserAccessHash,
    ): UserDetails? {
        val user = UsersService
            .details(context, authorization.id, listOf(id))
            .first()
            ?: return null
        if (accessHash != user.accessHash) {
            return null
        }
        return user
    }

    suspend fun commonFriendIds(
        context: AppContext,
        firstUserId: UserId,
        secondUserId: UserId,
    ): List<UserId> {
        val firstFriends = listIds(context, fromId = firstUserId)
        val secondFriends = listIds(context, fromId = secondUserId)
        return firstFriends.intersect(secondFriends).toList()
    }

    suspend fun list(context: AppContext, fromId: UserId): List<UserDetails> {
        val friendIds = listIds(context, fromId)
        val friendDetails = UsersService.details(
            context = context,
            fromId = fromId,
            ids = friendIds,
        ).map { details ->
            details ?: error(
                "User not found, but it is unexpected since all friend ids must be existing users",
            )
        }
        return friendDetails
    }

    suspend fun listIds(context: AppContext, fromId: UserId): List<UserId> =
        suspendTransaction(context.database) {
            val outgoingEntries = FriendsTable
                .selectOutgoing(listOf(fromId))
                .asReversed()
            val outgoingDescriptors = outgoingEntries
                .filter { entry -> entry.decision == Request }
                .map { entry -> entry.descriptor }
            val incomingDescriptors = outgoingDescriptors
                .map { descriptor -> descriptor.swap() }
            val incomingDecisions = FriendsTable
                .select(incomingDescriptors)
                .iterator()
            val mutualFriends = outgoingDescriptors
                .filter { incomingDecisions.next() == Request }
                .map { descriptor -> descriptor.toId }
            mutualFriends
        }

    suspend fun areFriends(
        context: AppContext,
        first: UserId,
        second: UserId,
    ): Boolean {
        val descriptor = FriendsTable.Descriptor(first, second)
        return suspendTransaction(context.database) {
            FriendsTable.select(
                descriptors = listOf(descriptor, descriptor.swap()),
            )
        }.all { decision -> decision == Request }
    }
}
