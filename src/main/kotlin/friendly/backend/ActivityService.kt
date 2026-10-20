package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object ActivityService {
    sealed interface ListResult {
        data object Unauthorized : ListResult
        data object CursorInvalid : ListResult
        data class Success(val cursor: Cursor<ActivityDetails>) : ListResult
    }

    suspend fun list(
        context: AppContext,
        authorization: Authorization,
        cursorId: CursorId?,
    ): ListResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        val before = cursorId?.toActivityId {
            return CursorInvalid
        }
        return suspendTransaction(context.database) {
            val (entries, hasNext) = ActivityTable.select(
                toId = authorization.id,
                before = before,
                limit = 100,
            )
            val nextId = if (hasNext) {
                CursorId("${entries.last().id.long}")
            } else {
                null
            }
            val details = details(
                context = context,
                fromId = authorization.id,
                entries = entries,
            )
            val cursor = Cursor(details, nextId)
            ListResult.Success(cursor)
        }
    }

    sealed interface NewUnreadsResult {
        data object Unauthorized : NewUnreadsResult
        data object None : NewUnreadsResult
        data class Some(val activityId: ActivityId) : NewUnreadsResult
    }

    suspend fun newUnreads(
        context: AppContext,
        authorization: Authorization,
    ): NewUnreadsResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            val activityId = ActivityNewUnreadsTable.select(authorization.id)
            if (activityId == null) {
                NewUnreadsResult.None
            } else {
                NewUnreadsResult.Some(activityId)
            }
        }
    }

    sealed interface AcknowledgeUnreadsResult {
        data object Unauthorized : AcknowledgeUnreadsResult
        data object Ok : AcknowledgeUnreadsResult
    }

    suspend fun acknowledgeUnreads(
        context: AppContext,
        authorization: Authorization,
    ): AcknowledgeUnreadsResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            ActivityNewUnreadsTable.delete(authorization.id)
            AcknowledgeUnreadsResult.Ok
        }
    }

    sealed interface ReadResult {
        data object Unauthorized : ReadResult
        data object NotFound : ReadResult
        data object Success : ReadResult
    }

    suspend fun read(
        context: AppContext,
        authorization: Authorization,
        id: ActivityId,
    ): ReadResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            val activity = ActivityTable.selectById(listOf(id)).first()
            if (activity == null || activity.toId != authorization.id) {
                return@suspendTransaction NotFound
            }
            ActivityTable.markAsRead(id)
            ActivityNewUnreadsTable.delete(authorization.id, id)
            Success
        }
    }

    suspend fun details(
        context: AppContext,
        fromId: UserId,
        entries: List<ActivityEntry>,
    ): List<ActivityDetails> {
        val indirectReplies =
            entries.filterIsInstance<ActivityEntry.IndirectReplies>()
        val commonDenominators =
            findCommonDenominators(context, fromId, indirectReplies)

        val postIds = entries.flatMap { entry ->
            when (entry) {
                is Reply -> listOf(entry.postId)
                is IndirectReplies -> listOf(
                    entry.postId,
                    commonDenominators.getValue(entry.id),
                )
            }
        }
        val posts = CommunityService
            .detailsFromIds(context, fromId, postIds, withDeleted = false)
            .iterator()
        return entries.map { entry ->
            when (entry) {
                is Reply -> ActivityDetails.Reply(
                    id = entry.id,
                    instant = entry.instant,
                    isRead = entry.isRead,
                    post = posts.next() as Plain,
                )
                is IndirectReplies -> ActivityDetails.IndirectReplies(
                    id = entry.id,
                    instant = entry.instant,
                    isRead = entry.isRead,
                    post = posts.next() as Plain,
                    commonDenominator = posts.next() as Plain,
                )
            }
        }
    }

    suspend fun findCommonDenominators(
        context: AppContext,
        fromId: UserId,
        indirectReplies: List<ActivityEntry.IndirectReplies>,
    ): Map<ActivityId, CommunityPostId> = suspendTransaction(context.database) {
        val limit = 100
        val unreads = CommunityUnreadsTable
            .selectTransitiveGroupedUnreads(
                userId = fromId,
                postIds = indirectReplies.map { entry -> entry.postId },
                limit = limit + 1,
            )
        val upstreams = CommunityPostsPathTable
            .selectUpstreamBatch(
                postIds = unreads.values.flatten(),
            )
        indirectReplies.associate local@{ entry ->
            val unreads = unreads[entry.postId].orEmpty()
            if (unreads.isEmpty()) {
                return@local entry.id to entry.postId
            }
            if (unreads.size == 1) {
                return@local entry.id to unreads.first()
            }
            var commonDenominator = entry.postId
            if (unreads.size > limit) {
                return@local entry.id to commonDenominator
            }
            var i = 0
            while (true) {
                val ancestors = unreads.map { unread ->
                    upstreams.getValue(unread).getOrNull(i)
                }
                val hasIndexOverflow = ancestors.any { ancestor ->
                    ancestor == null
                }
                if (hasIndexOverflow) break
                val hasDifferentAncestors = ancestors.distinct().size != 1
                if (hasDifferentAncestors) break
                commonDenominator = ancestors.first() ?: error(
                    "bug! if there are null ancestors, should break early",
                )
                i++
            }
            entry.id to commonDenominator
        }
    }

    suspend fun addReply(
        context: AppContext,
        toId: UserId,
        postId: CommunityPostId,
    ) {
        suspendTransaction(context.database) {
            val instant = context.clock.now()
            val reply = ActivityPayload.Reply(toId, instant, postId)
            val activityId = ActivityTable.insert(listOf(reply)).first()
            onActivityCreated(context, mapOf(toId to activityId))
        }
    }

    suspend fun addIndirectReplies(
        context: AppContext,
        fromId: UserId,
        postId: CommunityPostId,
        text: CommunityPostText,
        replyToOwnerId: UserId,
    ) {
        suspendTransaction(context.database) t@{
            if (text.isOnlyEmojis) return@t
            val upstream = CommunityPostsPathTable.selectUpstream(postId)
            val rootId = upstream.getOrElse(0) { return@t }
            val alreadyNotified = ActivityTable
                .selectUnreadReplies(upstream)
                .map { reply -> reply.toId }
            val subscribers = CommunitySubscribersTable.selectUsers(rootId)
                .toSet()
                .let { subscribers ->
                    subscribers - replyToOwnerId - fromId - alreadyNotified
                }
                .toList()
            ActivityTable.deleteIndirectRepliesByUserIds(
                toIds = subscribers,
                postId = rootId,
            )
            val instant = context.clock.now()
            val payload = subscribers.map { subscriber ->
                ActivityPayload.IndirectReplies(
                    toId = subscriber,
                    instant = instant,
                    postId = rootId,
                )
            }
            val activityIds = ActivityTable.insert(payload).iterator()
            val activity = payload.associate { payload ->
                payload.toId to activityIds.next()
            }
            onActivityCreated(context, activity)
        }
    }

    suspend fun onActivityCreated(
        context: AppContext,
        activity: Map<UserId, ActivityId>,
    ) {
        suspendTransaction(context.database) {
            ActivityNewUnreadsTable.upsert(activity)
        }
    }

    suspend fun onPostCreated(
        context: AppContext,
        fromId: UserId,
        id: CommunityPostId,
        text: CommunityPostText,
        replyTo: CommunityPostsTable.Entry?,
    ) {
        suspendTransaction(context.database) t@{
            val replyToOwnerId = replyTo?.ownerId ?: return@t
            val isNotSelfReply = replyToOwnerId != fromId
            if (isNotSelfReply) {
                addReply(context, replyToOwnerId, id)
            }
            addIndirectReplies(
                context = context,
                fromId = fromId,
                postId = id,
                text = text,
                replyToOwnerId = replyToOwnerId,
            )
        }
    }

    suspend fun onPostRead(
        context: AppContext,
        fromId: UserId,
        ids: List<CommunityPostId>,
    ) {
        suspendTransaction(context.database) {
            val markedReplies = ActivityTable.markRepliesAsRead(
                toId = fromId,
                postIds = ids,
            )
            val rootIds = CommunityPostsPathTable.selectRootBatch(ids).values
            val hasUnreads = CommunityUnreadsTable.selectTransitiveNewUnreads(
                userId = fromId,
                postIds = rootIds.toList(),
            ).keys
            val markedIndirectReplies = ActivityTable.markIndirectRepliesAsRead(
                toId = fromId,
                postIds = rootIds.filter { rootId -> rootId !in hasUnreads },
            )
            ActivityNewUnreadsTable.delete(
                activityIds = markedReplies + markedIndirectReplies,
            )
        }
    }

    suspend fun onPostDeleted(
        context: AppContext,
        id: CommunityPostId,
        upstream: List<CommunityPostId>,
    ) {
        suspendTransaction(context.database) t@{
            ActivityTable.deleteReplies(id)
            ActivityTable.deleteIndirectReplies(id)
            val rootId = upstream.firstOrNull() ?: return@t
            ActivityTable.deleteIndirectRepliesWithoutUnreads(rootId)
        }
    }
}
