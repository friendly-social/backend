package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

object FriendsService {

    sealed interface GenerateResult {
        data object Unauthorized : GenerateResult
        data class Success(val token: FriendToken, val expiration: Instant) :
            GenerateResult
    }

    suspend fun generate(
        context: AppContext,
        authorization: Authorization,
    ): GenerateResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return GenerateResult.Unauthorized }
        return suspendTransaction(context.database) {
            val previousEntry = FriendTokensTable.select(authorization.id)
            val now = context.clock.now()
            if (
                previousEntry != null &&
                previousEntry.expiration - now > 1.hours
            ) {
                GenerateResult.Success(
                    token = previousEntry.token,
                    expiration = previousEntry.expiration,
                )
            } else {
                generateForce(context, authorization.id)
            }
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
            generateForce(context, authorization.id)
        }
    }

    suspend fun generateForce(
        context: AppContext,
        userId: UserId,
    ): GenerateResult.Success = suspendTransaction(context.database) {
        FriendTokensTable.delete(userId)
        val token = FriendToken.random(context.random)
        val expiration = context.clock.now() + 1.days
        FriendTokensTable.insert(userId, token, expiration)
        GenerateResult.Success(token, expiration)
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
    ): AddResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return AddResult.Unauthorized }
        if (addViaPermanentLink(context, authorization, token)) {
            return Success
        }
        return suspendTransaction(context.database) t@{
            val entry = FriendTokensTable.select(token)
                ?: return@t AddResult.FriendTokenExpired
            if (entry.ownerId == authorization.id) {
                return@t AddResult.Success
            }
            val isTokenValid = context.clock.now() < entry.expiration
            val outgoing = FriendsTable.select(
                descriptors = listOf(
                    FriendsTable.Descriptor(
                        fromId = authorization.id,
                        toId = entry.ownerId,
                    ),
                ),
            ).first()
            val shouldSendNotification = outgoing == null
            if (isTokenValid) {
                FriendsTable.upsert(
                    fromId = authorization.id,
                    toId = entry.ownerId,
                    decision = Request,
                    declineTimes = 0,
                    declineForgetRandom = 0.milliseconds,
                    instant = context.clock.now(),
                )
                FriendsTable.upsert(
                    fromId = entry.ownerId,
                    toId = authorization.id,
                    decision = Request,
                    declineTimes = 0,
                    declineForgetRandom = 0.milliseconds,
                    instant = context.clock.now(),
                )
                if (shouldSendNotification) {
                    ActivityService.onNewRequest(
                        context = context,
                        fromId = authorization.id,
                        toId = entry.ownerId,
                        isMutual = true,
                    )
                }
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
    ): Boolean {
        val link = PermanentLinks.Entries
            .firstOrNull { (permanentToken) -> permanentToken == token }
            ?: return false
        val userId = link.userId
        suspendTransaction(context.database) {
            FriendsTable.upsert(
                fromId = authorization.id,
                toId = userId,
                decision = Request,
                declineTimes = 0,
                declineForgetRandom = 0.milliseconds,
                instant = context.clock.now(),
            )
            FriendsTable.upsert(
                fromId = userId,
                toId = authorization.id,
                decision = Request,
                declineTimes = 0,
                declineForgetRandom = 0.milliseconds,
                instant = context.clock.now(),
            )
        }
        return true
    }

    sealed interface AddDetailsResult {
        data object NotFound : AddDetailsResult
        data class Success(
            val nickname: Nickname,
            val description: UserDescription,
            val interests: InterestList,
            val avatar: FileDescriptor?,
        ) : AddDetailsResult
    }

    suspend fun addDetails(
        context: AppContext,
        token: FriendToken,
    ): AddDetailsResult {
        return suspendTransaction(context.database) t@{
            val entry = FriendTokensTable.select(token)
                ?: return@t AddDetailsResult.NotFound
            val userEntry = UsersTable
                .select(listOf(entry.ownerId))
                .getValue(entry.ownerId)
            val interests = InterestsTable
                .select(listOf(userEntry.id))
                .first()
                .list
            AddDetailsResult.Success(
                nickname = userEntry.nickname,
                description = userEntry.description,
                avatar = userEntry.avatar,
                interests = interests,
            )
        }
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
            ).map { entry -> entry?.decision }
        }
        return suspendTransaction(context.database) {
            val shouldSendNotification = outgoing == null
            if (shouldSendNotification) {
                if (incoming != Decline) {
                    val isMutual = incoming == Request
                    val notification = NotificationPayload.NewRequest(
                        toId = userId,
                        fromId = authorization.id,
                        isMutual = isMutual,
                    )
                    ActivityService.onNewRequest(
                        context = context,
                        fromId = authorization.id,
                        toId = userId,
                        isMutual = isMutual,
                    )
                    NotificationsService.schedule(context, notification)
                }
            }
            FriendsTable.upsert(
                fromId = authorization.id,
                toId = userId,
                decision = Request,
                declineTimes = 0,
                declineForgetRandom = 0.milliseconds,
                instant = context.clock.now(),
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
        val declineForgetRandom = (0..30).random(context.random).days
        return suspendTransaction(context.database) {
            val declineTimes = FriendsTable.select(
                listOf(
                    FriendsTable.Descriptor(
                        fromId = authorization.id,
                        toId = userId,
                    ),
                ),
            ).first()?.declineTimes ?: 0
            FriendsTable.upsert(
                fromId = authorization.id,
                toId = userId,
                decision = Decline,
                declineTimes = declineTimes + 1,
                declineForgetRandom = declineForgetRandom,
                instant = context.clock.now(),
            )
            ActivityService.onNewDecline(
                context = context,
                fromId = authorization.id,
                toId = userId,
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
            .values.single()
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
        val friendDetails = UsersService.detailsLegacy(
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
                .filter { entry -> entry.decision == Request }
            val incomingEntries = FriendsTable.select(
                descriptors = outgoingEntries.map { entry ->
                    entry.descriptor.swap()
                },
            )
            val mutualFriends = outgoingEntries.zip(incomingEntries)
                .filter { (_, incoming) ->
                    incoming?.decision == Request
                }
                .sortedByDescending { (outgoing, incoming) ->
                    maxOf(outgoing.id.long, incoming!!.id.long)
                }
                .map { (outgoing) -> outgoing.toId }
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
        }.all { entry -> entry?.decision == Request }
    }
}
