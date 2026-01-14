package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UsersService {

    data class CreateResult(val id: UserId, val accessHash: UserAccessHash)

    suspend fun impureCreate(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
        avatar: FileDescriptor?,
        socialLink: SocialLink?,
    ): CreateResult = suspendTransaction(context.database) {
        val accessHash = UserAccessHash.impureRandom(context.random)
        val id = UsersTable.impureInsert(
            accessHash = accessHash,
            nickname = nickname,
            description = description,
            avatar = avatar,
            socialLink = socialLink,
        )
        InterestsTable.impureInsert(id, interests)
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

    suspend fun impureDetails(
        context: AppContext,
        authorization: Authorization,
        descriptor: DetailsDescriptor,
    ): DetailsResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return DetailsResult.Unauthorized }
        val descriptorId = when (descriptor) {
            is Self -> authorization.id
            is Other -> descriptor.id
        }
        return suspendTransaction(context.database) {
            val details = impureDetails(context, listOf(descriptorId)).first()
            if (details == null) {
                DetailsResult.NotFound
            } else {
                DetailsResult.Success(details)
            }
        }
    }

    suspend fun impureDetails(
        context: AppContext,
        ids: List<UserId>,
    ): List<UserDetails?> {
        return suspendTransaction(context.database) {
            val entries = UsersTable.impureSelect(ids)
            val interests = InterestsTable.impureSelect(ids)
            entries.zip(interests) { entry, interests ->
                entry ?: return@zip null
                UserDetails(
                    id = entry.id,
                    accessHash = entry.accessHash,
                    nickname = entry.nickname,
                    description = entry.description,
                    avatar = entry.avatar,
                    interests = interests.list,
                    socialLink = entry.socialLink,
                )
            }
        }
    }
}
