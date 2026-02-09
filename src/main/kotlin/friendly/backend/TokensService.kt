package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object TokensService {

    data class GenerateResult(
        val token: Token,
        val id: UserId,
        val accessHash: UserAccessHash,
    )

    suspend fun generate(
        context: AppContext,
        nickname: Nickname,
        description: UserDescription,
        interests: InterestList,
        avatar: FileDescriptor?,
        socialLink: SocialLink?,
    ): GenerateResult {
        val token = Token.random(context.random)
        return suspendTransaction(context.database) {
            val (id, accessHash) = UsersService.create(
                context = context,
                nickname = nickname,
                description = description,
                interests = interests,
                avatar = avatar,
                socialLink = socialLink,
            )
            TokensTable.insert(token, id)
            GenerateResult(token, id, accessHash)
        }
    }

    suspend fun firebase(
        context: AppContext,
        authorization: Authorization,
        firebaseToken: FirebaseToken,
    ) = suspendTransaction(context.database) {
        TokensTable.updateFirebase(
            ownerId = authorization.id,
            token = authorization.token,
            firebaseToken = firebaseToken,
        )
    }

    suspend fun add(context: AppContext, id: UserId): Token {
        val token = Token.random(context.random)
        return suspendTransaction(context.database) {
            TokensTable.insert(token, id)
            token
        }
    }

    suspend fun logout(context: AppContext, authorization: Authorization) =
        suspendTransaction(context.database) {
            TokensTable.delete(
                ownerId = authorization.id,
                token = authorization.token,
            )
        }
}
