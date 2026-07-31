package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object CommunityService {
    sealed interface PostResult {
        data object Unauthorized : PostResult
        data object NotFound : PostResult
        data class Success(val descriptor: CommunityPostDescriptor) : PostResult
    }

    suspend fun post(
        context: AppContext,
        authorization: Authorization,
        text: CommunityPostText,
        replyTo: CommunityPostDescriptor?,
    ): PostResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        if (replyTo != null) {
            val noPost = suspendTransaction(context.database) {
                !CommunityPostsTable.exists(replyTo)
            }
            if (noPost) return NotFound
        }
        val now = context.clock.now()
        val accessHash = CommunityPostAccessHash.random(context.random)
        val id = suspendTransaction(context.database) {
            CommunityPostsTable.insert(
                accessHash = accessHash,
                ownerId = authorization.id,
                text = text,
                instant = now,
                replyTo = replyTo?.id,
            )
        }
        val descriptor = CommunityPostDescriptor(id, accessHash)
        return PostResult.Success(descriptor)
    }

    sealed interface RepliesResult {
        data object Unauthorized : RepliesResult
        data object CursorInvalid : RepliesResult
        data object NotFound : RepliesResult
        data class Success(val cursor: Cursor<CommunityPost>) : RepliesResult
    }

    suspend fun replies(
        context: AppContext,
        authorization: Authorization,
        replyTo: CommunityPostDescriptor,
        cursorId: CursorId?,
    ): RepliesResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val before = cursorId?.toCommunityPostId { return CursorInvalid }
        val noPost = suspendTransaction(context.database) {
            !CommunityPostsTable.exists(replyTo)
        }
        if (noPost) return NotFound
        val (postRecords, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.select(replyTo.id, before, limit = 1000)
        }
        val users = UsersService.detailsOrThrow(
            context = context,
            fromId = authorization.id,
            ids = postRecords.map { record -> record.ownerId },
        )
        val posts = postRecords.zip(users) { record, owner ->
            record.toPost(owner)
        }
        val nextId = posts.lastOrNull()?.id?.toCursorId()
        val cursor = Cursor(
            data = posts,
            nextId = nextId.takeIf { hasNext },
        )
        return RepliesResult.Success(cursor)
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
        val posts = postRecords.zip(users) { record, owner ->
            record.toPost(owner)
        }
        val nextId = posts.lastOrNull()?.id?.toCursorId()
        val cursor = Cursor(
            data = posts,
            nextId = nextId.takeIf { hasNext },
        )
        return ListResult.Success(cursor)
    }

    sealed interface DeleteResult {
        data object Unauthorized : DeleteResult
        data object NotFound : DeleteResult
        data object Success : DeleteResult
    }

    suspend fun delete(
        context: AppContext,
        authorization: Authorization,
        id: CommunityPostId,
    ): DeleteResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val exists = suspendTransaction(context.database) {
            CommunityPostsTable.delete(id, authorization.id)
        }
        if (!exists) {
            return NotFound
        }
        return Success
    }

    sealed interface EditResult {
        data object Unauthorized : EditResult
        data object NotFound : EditResult
        data object Success : EditResult
    }

    suspend fun edit(
        context: AppContext,
        authorization: Authorization,
        id: CommunityPostId,
        text: Field<CommunityPostText>?,
    ): EditResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val exists = suspendTransaction(context.database) {
            CommunityPostsTable.update(id, authorization.id, text)
        }
        if (!exists) {
            return NotFound
        }
        return Success
    }

    fun CommunityPostsTable.Entry.toPost(owner: UserDetails): CommunityPost =
        CommunityPost(
            id = id,
            accessHash = accessHash,
            text = text,
            owner = owner,
            instant = instant,
            edited = edited,
        )
}
