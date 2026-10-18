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
        val postIds = entries.map { entry ->
            when (entry) {
                is Reply -> entry.postId
                is IndirectReplies -> entry.postId
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
                )
            }
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
        subscribers: List<UserId>,
        postId: CommunityPostId,
        newUnreadId: CommunityPostId,
    ) {
        suspendTransaction(context.database) {
            val instant = context.clock.now()
            val payload = subscribers.map { subscriber ->
                ActivityPayload.IndirectReplies(
                    toId = subscriber,
                    instant = instant,
                    postId = postId,
                    newUnreadId = newUnreadId,
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

    // todo: do not send reply to the first user upstream who didn't yet read
    //       reply activity entry yet.
    // todo: calculate common post reply, so user is sent where latest activity
    //       actually is and not just to the root post
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
                ActivityService.addReply(context, replyToOwnerId, id)
            }
            if (text.isEmoji) return@t
            val upstream = CommunityPostsPathTable.selectUpstream(id)
            val rootId = upstream.getOrElse(0) {
                error("bug: checked that this post is reply")
            }
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
            addIndirectReplies(
                context = context,
                subscribers = subscribers,
                postId = rootId,
                newUnreadId = id,
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
            // todo: mark only if all posts are read, when multi-posts are
            //       implemented
            val markedIndirectReplies = ActivityTable
                .markIndirectRepliesAsRead(
                    toId = fromId,
                    newUnreadIds = ids,
                )
            ActivityNewUnreadsTable.delete(
                activityIds = markedReplies + markedIndirectReplies,
            )
        }
    }

    // todo: when compression for indirect_replies is added,
    //       deleteIndirectReplies should not be invoked if it can be rolled
    //       back.
    //
    //       Create a separate table for an array new replies that are
    //       compressed (also add a limit).
    suspend fun onPostDeleted(context: AppContext, postId: CommunityPostId) {
        suspendTransaction(context.database) {
            ActivityTable.deleteReplies(postId)
            ActivityTable.deleteIndirectReplies(postId)
            ActivityTable.deleteIndirectRepliesByNewUnread(newUnreadId = postId)
        }
    }
}
