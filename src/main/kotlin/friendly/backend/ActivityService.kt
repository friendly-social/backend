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
            val activityId = ActivityTable.insert(reply)
            onActivityCreated(context, toId, activityId)
        }
    }

    suspend fun onActivityCreated(
        context: AppContext,
        toId: UserId,
        activityId: ActivityId,
    ) {
        suspendTransaction(context.database) {
            ActivityNewUnreadsTable.upsert(toId, activityId)
        }
    }

    suspend fun onPostCreated(
        context: AppContext,
        fromId: UserId,
        id: CommunityPostId,
        replyTo: CommunityPostsTable.Entry?,
    ) {
        val replyOwnerId = replyTo?.ownerId ?: return
        val isNotSelfReply = replyOwnerId != fromId
        if (isNotSelfReply) {
            suspendTransaction(context.database) {
                ActivityService.addReply(context, replyOwnerId, id)
            }
        }
    }

    suspend fun onPostRead(
        context: AppContext,
        fromId: UserId,
        ids: List<CommunityPostId>,
    ) {
        suspendTransaction(context.database) {
            val markedIds = ActivityTable.markRepliesAsRead(
                toId = fromId,
                postIds = ids,
            )
            ActivityNewUnreadsTable.delete(activityIds = markedIds)
        }
    }

    suspend fun onPostDeleted(context: AppContext, postId: CommunityPostId) {
        suspendTransaction(context.database) {
            ActivityTable.deleteReplies(postId)
        }
    }
}
