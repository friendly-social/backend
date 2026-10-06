package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UsersService {

    data class CreateResult(val id: UserId, val accessHash: UserAccessHash)

    suspend fun create(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: InterestList,
        avatar: FilePreuploadDescriptor?,
        socialLink: SocialLink?,
    ): CreateResult = suspendTransaction(context.database) {
        val accessHash = UserAccessHash.random(context.random)
        val ownerId = UsersTable.insert(
            accessHash = accessHash,
            nickname = nickname,
            description = description,
            socialLink = socialLink,
        )
        InterestsTable.insert(ownerId, interests)
        val avatarCompleted = if (avatar != null) {
            val result = FilesService.preuploadComplete(
                context = context,
                ownerId = ownerId,
                descriptor = avatar,
            )
            when (result) {
                is NotFound -> {
                    val now = context.clock.now()
                    val alert = AlertPayload.SignUpAvatarNotFound(now)
                    AlertsService.post(context, alert)
                    null
                }
                is Ok -> result.descriptor
            }
        } else {
            null
        }
        UsersTable.updateAvatar(
            id = ownerId,
            avatar = avatarCompleted,
        )
        CreateResult(ownerId, accessHash)
    }

    sealed interface DetailsDescriptor {
        data object Self : DetailsDescriptor
        data class Other(val id: UserId, val accessHash: UserAccessHash) :
            DetailsDescriptor
    }

    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data object NotFound : DetailsResult
        data class Success(
            val user: UserDetails,
            val commonFriends: List<UserDetails>?,
        ) : DetailsResult
    }

    suspend fun details(
        context: AppContext,
        authorization: Authorization,
        descriptor: DetailsDescriptor,
    ): DetailsResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return DetailsResult.Unauthorized }
        val descriptorId = when (descriptor) {
            is Self -> authorization.id
            is Other -> descriptor.id
        }
        return suspendTransaction(context.database) {
            val user = detailsLegacy(
                context = context,
                fromId = authorization.id,
                ids = listOf(descriptorId),
            ).first()
            val hashInvalid = descriptor is Other &&
                descriptor.accessHash != user?.accessHash
            if (user == null || hashInvalid) {
                return@suspendTransaction DetailsResult.NotFound
            }
            val commonFriends = if (descriptorId == authorization.id) {
                null
            } else {
                val ids = FriendsService.commonFriendIds(
                    context = context,
                    firstUserId = authorization.id,
                    secondUserId = descriptorId,
                )
                UsersService.detailsLegacy(
                    context = context,
                    fromId = authorization.id,
                    ids = ids,
                ).map { user -> user ?: error("User $user not found") }
            }
            DetailsResult.Success(user, commonFriends)
        }
    }

    suspend fun details(
        context: AppContext,
        fromId: UserId,
        descriptor: UserDescriptor,
    ): UserDetails? = details(
        context = context,
        fromId = fromId,
        ids = listOf(descriptor.id),
    ).values.first()?.takeIf { user ->
        user.accessHash == descriptor.accessHash
    }

    suspend fun detailsLegacy(
        context: AppContext,
        fromId: UserId,
        ids: List<UserId>,
    ): List<UserDetails?> {
        return suspendTransaction(context.database) {
            val entries = UsersTable.selectLegacy(ids)
            val interests = InterestsTable.select(ids).iterator()
            val email = selectEmailIfOwner(fromId, ids)
            val friendship = friendship(fromId, ids).iterator()
            entries.map { entry ->
                val interests = interests.next()
                val friendship = friendship.next()
                entry ?: return@map null
                UserDetails(
                    id = entry.id,
                    accessHash = entry.accessHash,
                    nickname = entry.nickname,
                    email = hideEmailIfNotOwner(fromId, entry.id, email),
                    description = entry.description,
                    avatar = entry.avatar,
                    interests = interests.list,
                    socialLink = entry.socialLink,
                    friendship = friendship,
                )
            }
        }
    }

    suspend fun details(
        context: AppContext,
        fromId: UserId,
        ids: List<UserId>,
    ): Map<UserId, UserDetails> {
        return suspendTransaction(context.database) {
            val entries = UsersTable.select(ids)
            val interests = InterestsTable.select(ids).iterator()
            val email = selectEmailIfOwner(fromId, ids)
            val friendship = friendship(fromId, ids).iterator()
            ids.mapNotNull { id ->
                val entry = entries.getValue(id)
                val interests = interests.next()
                val friendship = friendship.next()
                entry ?: return@mapNotNull null
                UserDetails(
                    id = entry.id,
                    accessHash = entry.accessHash,
                    nickname = entry.nickname,
                    email = hideEmailIfNotOwner(fromId, entry.id, email),
                    description = entry.description,
                    avatar = entry.avatar,
                    interests = interests.list,
                    socialLink = entry.socialLink,
                    friendship = friendship,
                )
            }.associateBy { user -> user.id }
        }
    }

    private suspend fun friendship(
        fromId: UserId,
        ids: List<UserId>,
    ): List<Friendship> {
        val descriptors = ids.flatMap { id ->
            val outgoing = FriendsTable.Descriptor(fromId, id)
            val incoming = outgoing.swap()
            listOf(outgoing, incoming)
        }
        val decisions = FriendsTable.select(descriptors)
        return decisions.chunked(2).map { (outgoingEntry, incomingEntry) ->
            val outgoing = outgoingEntry?.decision
            val incoming = incomingEntry?.decision
            when {
                outgoing == Request && incoming == Request -> Friends
                outgoing == Request -> OutgoingRequest
                incoming == Request -> IncomingRequest
                outgoing == Decline -> OutgoingDecline
                else -> None
            }
        }
    }

    private suspend fun selectEmailIfOwner(
        fromId: UserId,
        userIds: List<UserId>,
    ): Email? = if (fromId in userIds) {
        EmailsTable.select(listOf(fromId)).first()
    } else {
        null
    }

    private fun hideEmailIfNotOwner(
        fromId: UserId,
        id: UserId,
        email: Email?,
    ): Email? = if (fromId == id) email else null

    sealed interface EditResult {
        data object Unauthorized : EditResult
        data object Success : EditResult
    }

    suspend fun edit(
        context: AppContext,
        authorization: Authorization,
        nickname: Field<Nickname>?,
        description: Field<UserDescription>?,
        interests: Field<InterestList>?,
        avatar: Field<FileDescriptor?>?,
        socialLink: Field<SocialLink?>?,
    ): EditResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            UsersTable.update(
                id = authorization.id,
                nickname = nickname,
                description = description,
                avatar = avatar,
                socialLink = socialLink,
            )
            InterestsTable.delete(authorization.id)
            if (interests != null) {
                InterestsTable.insert(authorization.id, interests.value)
            }
            Success
        }
    }
}
