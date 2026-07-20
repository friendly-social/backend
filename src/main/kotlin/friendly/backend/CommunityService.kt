package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object CommunityService {
    sealed interface PostResult {
        data object Unauthorized : PostResult
        data object Success : PostResult
    }

    // todo: no rate limits
    suspend fun post(
        context: AppContext,
        authorization: Authorization,
        text: CommunityPostText,
    ): PostResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val now = context.clock.now()
        suspendTransaction(context.database) {
            CommunityPostsTable.insert(
                ownerId = authorization.id,
                text = text,
                instant = now,
            )
        }
        return Success
    }

    sealed interface ListResult {
        data object Unauthorized : ListResult
        data class Success(val list: List<CommunityPost>) : ListResult
    }

    suspend fun list(
        context: AppContext,
        authorization: Authorization,
    ): ListResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val friends = FriendsService.list(context, authorization.id)
        val friendIds = friends.map { friend -> friend.id }
        val posts = suspendTransaction(context.database) {
            CommunityPostsTable
                .select(friendIds)
                .zip(friends) { (id, _, text, instant), friend ->
                    CommunityPost(
                        id = id,
                        text = text,
                        owner = friend,
                        instant = instant,
                    )
                }
        }
        return ListResult.Success(posts)
    }
}
