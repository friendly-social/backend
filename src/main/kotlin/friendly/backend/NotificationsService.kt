package friendly.backend

import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NotificationsService")

/**
 * NotificationsService will support any types of notifications and is a
 * high-level entry point to be used by other services. In the first
 * implementation is will use Firebase, but later this may include web-hooks and
 * other things.
 */
object NotificationsService {

    suspend fun schedule(context: AppContext, payload: NotificationPayload) {
        val notification = suspendTransaction(context.database) {
            NotificationsTable.insert(payload)
        }
        execute(context, notification)
    }

    fun restoreScheduled(context: AppContext) {
        context.notifications.gracefulScope.launch {
            val pending = suspendTransaction(context.database) {
                NotificationsTable.selectPending()
            }
            for (notification in pending) {
                execute(context, notification)
            }
        }
    }

    fun execute(context: AppContext, notification: NotificationEntry) {
        context.notifications.gracefulScope.launch(start = UNDISPATCHED) {
            try {
                val tokens = suspendTransaction(context.database) {
                    TokensTable.select(notification.toId)
                }
                context.notifications.queue.execute(notification.toId) {
                    coroutineScope {
                        for (token in tokens) {
                            launch {
                                send(context, token, notification.id)
                            }
                        }
                    }
                }
                logger.info("Notification sent: $notification")
            } catch (exception: Exception) {
                logger.info("Notification error: $exception")
                throw exception
            } finally {
                markAsSent(context, notification)
            }
        }
    }

    suspend fun send(
        context: AppContext,
        token: TokensTable.Entry,
        id: NotificationId,
    ) {
        val firebaseToken = token.firebaseToken ?: return
        exponentialRetry {
            FirebaseService.send(context, firebaseToken, id)
        }
    }

    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data object NotFound : DetailsResult
        data class Success(val details: NotificationDetails) : DetailsResult
    }

    suspend fun details(
        context: AppContext,
        authorization: Authorization,
        id: NotificationId,
    ): DetailsResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) {
            val notification = NotificationsTable.selectById(id)
            if (notification == null) {
                return@suspendTransaction DetailsResult.NotFound
            }
            if (notification.toId != authorization.id) {
                return@suspendTransaction DetailsResult.NotFound
            }
            val details = when (notification) {
                is NewRequest -> {
                    val ids = listOf(notification.fromId)
                    val from = UsersService
                        .details(context, notification.toId, ids)
                        .first() ?: error("User is required to be found")
                    NotificationDetails.NewRequest(from, notification.isMutual)
                }
                is NewReply -> {
                    val post = CommunityService.detailsFromIds(
                        context = context,
                        fromId = notification.toId,
                        ids = listOf(notification.postId),
                        withDeleted = false,
                    ).first() as Plain?
                    if (post == null) {
                        return@suspendTransaction NotFound
                    }
                    NotificationDetails.NewReply(post)
                }
            }
            DetailsResult.Success(details)
        }
    }

    private suspend inline fun exponentialRetry(block: () -> Boolean) {
        var currentTimeout = 100L
        while (true) {
            if (block()) break
            currentTimeout *= 2
            currentTimeout = currentTimeout.coerceAtMost(20_000)
            delay(currentTimeout)
        }
    }

    suspend fun markAsSent(
        context: AppContext,
        notification: NotificationEntry,
    ) {
        suspendTransaction(context.database) {
            NotificationsTable.markAsSent(notification.id)
        }
    }

    suspend fun onPostCreated(
        context: AppContext,
        fromId: UserId,
        replyTo: CommunityPostsTable.Entry?,
        id: CommunityPostId,
    ) {
        val replyOwnerId = replyTo?.ownerId ?: return
        val isNotSelfReply = replyOwnerId != fromId
        if (isNotSelfReply) {
            schedule(
                context = context,
                payload = NotificationPayload.NewReply(
                    toId = replyOwnerId,
                    postId = id,
                ),
            )
        }
    }

    suspend fun onPostDeleted(context: AppContext, postId: CommunityPostId) {
        suspendTransaction(context.database) {
            NotificationsTable.deleteReplies(postId)
        }
    }
}
