package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object CommunityService {
    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data object NotFound : DetailsResult
        data class Success(
            val post: CommunityPostDetails,
            val replies: Cursor<CommunityPostReply>,
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
                withDeleted = true,
            ).first()
            if (entry == null) {
                return@suspendTransaction DetailsResult.NotFound
            }
            val post = detailsFromEntries(
                context = context,
                fromId = authorization.id,
                entries = listOf(entry),
            ).first()
            val replies = replies(
                context = context,
                fromId = authorization.id,
                replyTo = post.id,
                after = null,
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
        val replyToEntry = if (replyTo != null) {
            suspendTransaction(context.database) {
                CommunityPostsTable
                    .selectByDescriptor(listOf(replyTo), withDeleted = true)
                    .first()
            } ?: return NotFound
        } else {
            null
        }
        val now = context.clock.now()
        val accessHash = CommunityPostAccessHash.random(context.random)
        val id = suspendTransaction(context.database) {
            val path = if (replyTo != null) {
                CommunityPostsPathTable.selectUpstream(
                    postId = replyTo.id,
                ).apply {
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
        ActivityService.onPostCreated(
            context = context,
            fromId = authorization.id,
            replyTo = replyToEntry,
            id = id,
        )
        NotificationsService.onPostCreated(
            context = context,
            fromId = authorization.id,
            replyTo = replyToEntry,
            id = id,
        )
        val descriptor = CommunityPostDescriptor(id, accessHash)
        return PostResult.Success(descriptor)
    }

    sealed interface RepliesResult {
        data object Unauthorized : RepliesResult
        data object CursorInvalid : RepliesResult
        data object NotFound : RepliesResult
        data class Success(val cursor: Cursor<CommunityPostReply>) :
            RepliesResult
    }

    suspend fun replies(
        context: AppContext,
        fromId: UserId,
        replyTo: CommunityPostId,
        after: CommunityPostId?,
    ): Cursor<CommunityPostReply> {
        val (entries, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.selectReplies(
                replyTo = replyTo,
                after = after,
                limit = 100,
                withDeleted = true,
            )
        }
        val posts = detailsFromEntries(context, fromId, entries)
        if (posts.size != 1) {
            val nextId = posts.lastOrNull()?.id?.toCursorId()
            return Cursor(
                data = posts.map { post ->
                    CommunityPostReply.Single(post)
                },
                nextId = nextId.takeIf { hasNext },
            )
        }
        val post = posts.first()
        val replies = suspendTransaction(context.database) {
            CommunityPostsPathTable
                .selectReplies(post.id)
                .groupBy { entry -> entry.replyToDepth }
        }
        val threadIds = replies.keys
            .sorted()
            .takeWhile { depth -> replies.getValue(depth).size == 1 }
            .map { depth -> replies.getValue(depth).single().postId }
        if (threadIds.isEmpty()) {
            return Cursor(
                data = listOf(
                    CommunityPostReply.Single(post),
                ),
                nextId = null,
            )
        }
        val thread = detailsFromIds(
            context = context,
            fromId = fromId,
            ids = threadIds,
            withDeleted = true,
        ).map { details ->
            details ?: error("Cannot find all posts ($threadIds)")
        }
        return Cursor(
            data = listOf(
                CommunityPostReply.Thread(
                    thread = listOf(post) + thread,
                ),
            ),
            nextId = null,
        )
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
        val after = cursorId?.toCommunityPostId { return CursorInvalid }
        val noPost = suspendTransaction(context.database) {
            !CommunityPostsTable.exists(replyTo, withDeleted = true)
        }
        if (noPost) return NotFound
        val cursor = replies(
            context = context,
            fromId = authorization.id,
            replyTo = replyTo.id,
            after = after,
        )
        return RepliesResult.Success(cursor)
    }

    suspend fun upstream(
        context: AppContext,
        fromId: UserId,
        postId: CommunityPostId,
    ): List<CommunityPostDetails> = suspendTransaction(context.database) {
        val path = CommunityPostsPathTable.selectUpstream(postId)
        val entries = CommunityPostsTable
            .selectById(path, withDeleted = true)
            .map { post -> post ?: error("entries") }
        detailsFromEntries(context, fromId, entries)
    }

    sealed interface ListResult {
        data object Unauthorized : ListResult
        data object CursorInvalid : ListResult
        data class Success(val cursor: Cursor<CommunityPostDetails.Plain>) :
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
        val (entries, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.selectFrom(
                ids = ids,
                before = before,
                limit = 1000,
                withDeleted = false,
            )
        }
        val posts = detailsFromEntries(context, authorization.id, entries)
            .map { post -> post as Plain }
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
        val (entries, hasNext) = suspendTransaction(context.database) {
            CommunityPostsTable.selectFrom(
                ids = listOf(user.id),
                before = before,
                limit = 1000,
                withDeleted = false,
            )
        }
        val posts = detailsFromEntries(
            context = context,
            fromId = authorization.id,
            entries = entries,
        )
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

        suspend fun transaction(): DeleteResult {
            val post = CommunityPostsTable.selectById(
                ids = listOf(id),
                withDeleted = false,
            ).first() ?: return NotFound
            if ((post as Plain).ownerId != authorization.id) {
                return NotFound
            }
            val (replies) = CommunityPostsTable.selectReplies(
                replyTo = post.id,
                after = null,
                limit = 1,
                withDeleted = true,
            )
            if (replies.isEmpty()) {
                CommunityPostsTable.delete(id)
            } else {
                CommunityPostsTable.updateToDeleted(id)
            }
            ActivityService.onPostDeleted(context, id)
            return Success
        }

        return suspendTransaction(context.database) {
            transaction()
        }
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

    suspend fun detailsFromIds(
        context: AppContext,
        fromId: UserId,
        ids: List<CommunityPostId>,
        withDeleted: Boolean,
    ): List<CommunityPostDetails?> {
        val entries = suspendTransaction(context.database) {
            CommunityPostsTable.selectById(ids, withDeleted)
        }
        val details = detailsFromEntries(
            context = context,
            fromId = fromId,
            entries = entries.filterNotNull(),
        ).iterator()
        return entries.map { entry ->
            if (entry == null) {
                null
            } else {
                details.next()
            }
        }
    }

    suspend fun detailsFromEntries(
        context: AppContext,
        fromId: UserId,
        entries: List<CommunityPostsTable.Entry>,
    ): List<CommunityPostDetails> {
        val users = UsersService.detailsOrThrow(
            context = context,
            fromId = fromId,
            ids = entries.mapNotNull { entry ->
                when (entry) {
                    is Plain -> entry.ownerId
                    is Deleted -> null
                }
            },
        ).iterator()
        val replyUserIds = suspendTransaction(context.database) {
            CommunityPostsTable.selectReplierIds(
                ids = entries.map { entry -> entry.id },
            )
        }
        val replyUsers = suspendTransaction(context.database) {
            UsersService.detailsOrThrow(
                context = context,
                fromId = fromId,
                ids = replyUserIds.flatten(),
            )
        }.iterator()
        val replyUserIdsIterator = replyUserIds.iterator()
        return entries.map { entry ->
            val replyUserIds = replyUserIdsIterator.next()
            entry.toPost(
                replyPreviews = replyUserIds.map {
                    replyUsers.next()
                },
                ownerIfPlain = { users.next() },
            )
        }
    }

    inline fun CommunityPostsTable.Entry.toPost(
        replyPreviews: List<UserDetails>,
        ownerIfPlain: () -> UserDetails,
    ): CommunityPostDetails = when (this) {
        is Plain -> CommunityPostDetails.Plain(
            id = id,
            accessHash = accessHash,
            instant = instant,
            replyPreviews = replyPreviews,
            text = text,
            owner = ownerIfPlain(),
            edited = edited,
        )
        is Deleted -> CommunityPostDetails.Deleted(
            id = id,
            accessHash = accessHash,
            instant = instant,
            replyPreviews = replyPreviews,
        )
    }
}
