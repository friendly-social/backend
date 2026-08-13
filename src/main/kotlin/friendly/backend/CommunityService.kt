package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object CommunityService {
    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data object NotFound : DetailsResult
        data class Success(
            val post: CommunityPostDetails,
            val replies: Cursor<CommunityPostDetails>,
            val upstream: List<CommunityPostDetails>,
        ) : DetailsResult
    }

    suspend fun details(
        context: AppContext,
        authorization: Authorization,
        descriptor: CommunityPostDescriptor,
    ): DetailsResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            val entry = CommunityPostsTable.selectByDescriptor(
                listOf(descriptor),
            ).first()
            if (entry == null) {
                return@suspendTransaction DetailsResult.NotFound
            }
            val owner = UsersService.detailsOrThrow(
                context = context,
                fromId = authorization.id,
                ids = listOf(entry.ownerId),
            ).first()
            val post = entry.toPost(owner)
            val replies = replies(
                context = context,
                fromId = authorization.id,
                replyTo = post.id,
                before = null,
            )
            val upstream = upstream(
                context = context,
                fromId = authorization.id,
                postId = post.id,
            )
            DetailsResult.Success(
                post = post,
                replies = replies,
                upstream = upstream,
            )
        }
    }

    sealed interface PostResult {
        data object Unauthorized : PostResult
        data object NotFound : PostResult
        data class Success(val descriptor: CommunityPostDescriptor) : PostResult
    }

    const val MAX_DEPTH = 1_000

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
            val path = if (replyTo != null) {
                CommunityPostsPathTable.select(postId = replyTo.id).apply {
                    if (size >= MAX_DEPTH) {
                        return@suspendTransaction null
                    }
                }
            } else {
                null
            }
            val id = CommunityPostsTable.insert(
                accessHash = accessHash,
                ownerId = authorization.id,
                text = text,
                instant = now,
                replyTo = replyTo?.id,
            )
            if (path != null && replyTo != null) {
                CommunityPostsPathTable.insert(id, path + replyTo.id)
            }
            id
        } ?: return PostResult.NotFound
        val descriptor = CommunityPostDescriptor(id, accessHash)
        return PostResult.Success(descriptor)
    }

    sealed interface RepliesResult {
        data object Unauthorized : RepliesResult
        data object CursorInvalid : RepliesResult
        data object NotFound : RepliesResult
        data class Success(val cursor: Cursor<CommunityPostDetails>) :
            RepliesResult
    }

    suspend fun replies(
        context: AppContext,
        fromId: UserId,
        replyTo: CommunityPostId,
        before: CommunityPostId?,
    ): Cursor<CommunityPostDetails> {
        val (postRecords, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.select(replyTo, before, limit = 1000)
        }
        val users = UsersService.detailsOrThrow(
            context = context,
            fromId = fromId,
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
        return cursor
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
        val cursor = replies(
            context = context,
            fromId = authorization.id,
            replyTo = replyTo.id,
            before = before,
        )
        return RepliesResult.Success(cursor)
    }

    suspend fun upstream(
        context: AppContext,
        fromId: UserId,
        postId: CommunityPostId,
    ): List<CommunityPostDetails> = suspendTransaction(context.database) {
        val path = CommunityPostsPathTable.select(postId)
        val entries = CommunityPostsTable.selectById(path)
        toPosts(context, fromId, entries)
    }

    sealed interface ListResult {
        data object Unauthorized : ListResult
        data object CursorInvalid : ListResult
        data class Success(val cursor: Cursor<CommunityPostDetails>) :
            ListResult
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
        val posts = toPosts(context, authorization.id, postRecords)
        val nextId = posts.lastOrNull()?.id?.toCursorId()
        val cursor = Cursor(
            data = posts,
            nextId = nextId.takeIf { hasNext },
        )
        return ListResult.Success(cursor)
    }

    sealed interface FromResult {
        data object Unauthorized : FromResult
        data object NotFound : FromResult
        data object CursorInvalid : FromResult
        data class Success(val cursor: Cursor<CommunityPostDetails>) :
            FromResult
    }

    suspend fun from(
        context: AppContext,
        authorization: Authorization,
        userDescriptor: UserDescriptor,
        cursorId: CursorId?,
    ): FromResult {
        val before = cursorId?.toCommunityPostId { return CursorInvalid }
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val user = UsersService.details(
            context = context,
            fromId = authorization.id,
            descriptor = userDescriptor,
        ) ?: return NotFound
        val selfPosts = authorization.id == userDescriptor.id
        if (!selfPosts) {
            val friends = FriendsService.areFriends(
                context = context,
                first = authorization.id,
                second = user.id,
            )
            if (!friends) {
                return NotFound
            }
        }
        val (postRecords, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.select(
                ids = listOf(user.id),
                before = before,
                limit = 1000,
            )
        }
        val posts = postRecords.map { record ->
            record.toPost(user)
        }
        val nextId = posts.lastOrNull()?.id?.toCursorId()
        val cursor = Cursor(
            data = posts,
            nextId = nextId.takeIf { hasNext },
        )
        return FromResult.Success(cursor)
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

    suspend fun toPosts(
        context: AppContext,
        fromId: UserId,
        entries: List<CommunityPostsTable.Entry>,
    ): List<CommunityPostDetails> {
        val users = UsersService.detailsOrThrow(
            context = context,
            fromId = fromId,
            ids = entries.map { entry -> entry.ownerId },
        )
        return entries.zip(users) { entry, owner ->
            entry.toPost(owner)
        }
    }

    fun CommunityPostsTable.Entry.toPost(
        owner: UserDetails,
    ): CommunityPostDetails = CommunityPostDetails(
        id = id,
        accessHash = accessHash,
        text = text,
        owner = owner,
        instant = instant,
        edited = edited,
    )
}
