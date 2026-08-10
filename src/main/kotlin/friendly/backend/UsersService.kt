package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UsersService {

    data class CreateResult(val id: UserId, val accessHash: UserAccessHash)

    suspend fun create(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: InterestList,
        avatar: FileDescriptor?,
        socialLink: SocialLink?,
    ): CreateResult = suspendTransaction(context.database) {
        val accessHash = UserAccessHash.random(context.random)
        val id = UsersTable.insert(
            accessHash = accessHash,
            nickname = nickname,
            description = description,
            avatar = avatar,
            socialLink = socialLink,
        )
        InterestsTable.insert(id, interests)
        CreateResult(id, accessHash)
    }

    sealed interface DetailsDescriptor {
        data object Self : DetailsDescriptor
        data class Other(val id: UserId, val accessHash: UserAccessHash) :
            DetailsDescriptor
    }

    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data object NotFound : DetailsResult
        data class Success(val details: UserDetails) : DetailsResult
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
            val details = details(
                context = context,
                fromId = authorization.id,
                ids = listOf(descriptorId),
            ).first()
            val hashInvalid = descriptor is Other &&
                descriptor.accessHash != details?.accessHash
            if (details == null || hashInvalid) {
                DetailsResult.NotFound
            } else {
                DetailsResult.Success(details)
            }
        }
    }

    suspend fun detailsOrThrow(
        context: AppContext,
        fromId: UserId,
        ids: List<UserId>,
    ): List<UserDetails> = details(context, fromId, ids).mapIndexed { i, user ->
        user ?: error("User with id ${ids[i]} has not been found")
    }

    suspend fun details(
        context: AppContext,
        fromId: UserId,
        descriptor: UserDescriptor,
    ): UserDetails? = details(
        context = context,
        fromId = fromId,
        ids = listOf(descriptor.id),
    ).first()?.takeIf { user ->
        user.accessHash == descriptor.accessHash
    }

    suspend fun details(
        context: AppContext,
        fromId: UserId,
        ids: List<UserId>,
    ): List<UserDetails?> {
        return suspendTransaction(context.database) {
            val entries = UsersTable.select(ids)
            val interests = InterestsTable.select(ids)
            val email = selectEmailIfOwner(fromId, ids)
            entries.zip(interests) { entry, interests ->
                entry ?: return@zip null
                UserDetails(
                    id = entry.id,
                    accessHash = entry.accessHash,
                    nickname = entry.nickname,
                    email = hideEmailIfNotOwner(fromId, entry.id, email),
                    description = entry.description,
                    avatar = entry.avatar,
                    interests = interests.list,
                    socialLink = entry.socialLink,
                )
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
