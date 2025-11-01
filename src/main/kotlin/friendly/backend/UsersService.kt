package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object UsersService {

    data class CreateResult(val id: UserId, val accessHash: UserAccessHash)

    suspend fun impureCreate(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
    ): CreateResult = suspendTransaction(context.database) {
        val accessHash = UserAccessHash.impureRandom(context.random)
        val id = UsersTable.impureInsert(accessHash, nickname, description)
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
            is Self -> authorization.userId
            is Other -> descriptor.id
        }
        return suspendTransaction(context.database) {
            val usersTableEntry = UsersTable.impureSelect(descriptorId)
            if (usersTableEntry == null) {
                DetailsResult.NotFound
            } else {
                val interests = InterestsTable.impureSelect(descriptorId)
                val details = UserDetails(
                    id = usersTableEntry.id,
                    accessHash = usersTableEntry.accessHash,
                    nickname = usersTableEntry.nickname,
                    description = usersTableEntry.description,
                    interests = interests,
                )
                DetailsResult.Success(details)
            }
        }
    }
}
