package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object TokensService {

    data class GenerateResult(
        val token: Token,
        val id: UserId,
        val accessHash: UserAccessHash,
    )

    suspend fun impureGenerate(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: List<Interest>,
        avatar: FileDescriptor?,
        socialLink: SocialLink?,
    ): GenerateResult {
        val token = Token.impureRandom(context.random)
        return suspendTransaction(context.database) {
            val (id, accessHash) = UsersService.impureCreate(
                context = context,
                nickname = nickname,
                description = description,
                interests = interests,
                avatar = avatar,
                socialLink = socialLink,
            )
            TokensTable.impureInsert(token, id)
            GenerateResult(token, id, accessHash)
        }
    }
}
