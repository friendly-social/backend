package friendly.backend

import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * NotificationsService will support any types of notifications and is a
 * high-level entry point to be used by other services. In the first
 * implementation is will use Firebase, but later this may include web-hooks and
 * other things.
 */
object NotificationsService {

    suspend fun impureSendNewRequest(
        context: AppContext,
        toId: UserId,
        fromId: UserId,
        isMutual: Boolean,
    ) {
        val notification = suspendTransaction(context.database) {
            NotificationsTable.impureInsertNewRequest(toId, fromId, isMutual)
        }
        impureSend(context, notification)
    }

    /**
     * It suspends until the retrieval of all the pending notifications and
     * after that resumes while offloading all the work in the scope.
     * That way we can avoid race-conditions.
     */
    suspend fun impureSendPending(context: AppContext) {
        val pending = suspendTransaction(context.database) {
            NotificationsTable.impureSelect()
        }
        for (notification in pending) {
            context.scope.launch(start = UNDISPATCHED) {
                impureSend(context, notification)
            }
        }
    }

    private suspend fun impureSend(
        context: AppContext,
        notification: NotificationsTable.Entry,
    ) {
        context.notifications.queue.execute(notification.toId) {
            lateinit var tokens: List<TokensTable.Entry>
            lateinit var notificationDetails: NotificationDetails
            suspendTransaction(context.database) {
                tokens = TokensTable.impureSelect(notification.toId)
                notificationDetails = impureDetails(context, notification)
            }
            shutdownResistant {
                coroutineScope {
                    for (entry in tokens) launch {
                        impureSendToToken(
                            context = context,
                            entry = entry,
                            notification = notificationDetails,
                        )
                    }
                }
            }
        }
    }

    private suspend fun impureSendToToken(
        context: AppContext,
        entry: TokensTable.Entry,
        notification: NotificationDetails,
    ) {
        val firebaseToken = entry.firebaseToken
        exponentialRetry {
            when {
                firebaseToken != null -> {
                    FirebaseService.impureSend(
                        context = context,
                        firebaseToken = firebaseToken,
                        notification = notification,
                    )
                }
                else -> true
            }
        }
    }

    suspend fun impureDetails(
        context: AppContext,
        notification: NotificationsTable.Entry,
    ): NotificationDetails = when (notification) {
        is NewRequest -> {
            val ids = listOf(notification.fromId)
            val from = UsersService
                .impureDetails(context, ids)
                .first() ?: error("User is required to be found")
            NotificationDetails.NewRequest(from, notification.isMutual)
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
}
