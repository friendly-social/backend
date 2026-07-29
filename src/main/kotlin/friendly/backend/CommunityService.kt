package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object CommunityService {
    sealed interface PostResult {
        data object Unauthorized : PostResult
        data object Success : PostResult
    }

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
        data object CursorInvalid : ListResult
        data class Success(val cursor: Cursor<CommunityPost>) : ListResult
    }

    suspend fun list(
        context: AppContext,
        authorization: Authorization,
        cursorId: CursorId?,
    ): ListResult {
        val before = cursorId?.toCommunityPostId { return CursorInvalid }
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val friends = FriendsService.listIds(context, authorization.id)
        val ids = friends + authorization.id
        val (postRecords, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.select(ids, before, limit = 1000)
        }
        val users = UsersService.detailsOrThrow(
            context = context,
            fromId = authorization.id,
            ids = postRecords.map { record -> record.ownerId },
        )
        val posts = postRecords.zip(users) { (id, _, text, instant), user ->
            CommunityPost(
                id = id,
                text = text,
                owner = user,
                instant = instant,
            )
        }
        val nextId = posts.lastOrNull()?.id?.toCursorId()
        val cursor = Cursor(
            data = posts,
            nextId = nextId.takeIf { hasNext },
        )
        return ListResult.Success(cursor)
    }
}
