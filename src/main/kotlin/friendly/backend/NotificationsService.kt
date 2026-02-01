package friendly.backend

import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * NotificationsService will support any types of notifications and is a
 * high-level entry point to be used by other services. In the first
 * implementation is will use Firebase, but later this may include web-hooks and
 * other things.
 */
object NotificationsService {

    suspend fun impureSchedule(
        context: AppContext,
        payload: NotificationPayload,
    ) {
        val notification = suspendTransaction(context.database) {
            NotificationsTable.impureInsert(payload)
        }
        impureExecute(context, notification)
    }

    fun impureRestoreScheduled(context: AppContext) {
        context.notifications.scope.launch {
            val pending = suspendTransaction(context.database) {
                NotificationsTable.impureSelect()
            }
            for (notification in pending) {
                impureExecute(context, notification)
            }
        }
    }

    fun impureExecute(context: AppContext, notification: NotificationRecord) {
        context.notifications.scope.launch(start = UNDISPATCHED) {
            suspendTransaction(context.database) {
                val details = impureDetails(context, notification)
                val tokens = TokensTable.impureSelect(notification.toId)
                context.notifications.queue.execute(notification.toId) {
                    for (token in tokens) {
                        launch {
                            impureSend(context, token, details)
                        }
                    }
                }
            }
        }
    }

    suspend fun impureSend(
        context: AppContext,
        token: TokensTable.Entry,
        notification: NotificationDetails,
    ) {
        val firebaseToken = token.firebaseToken ?: return
        exponentialRetry {
            FirebaseService.impureSend(context, firebaseToken, notification)
        }
    }

    suspend fun impureDetails(
        context: AppContext,
        notification: NotificationRecord,
    ): NotificationDetails = suspendTransaction(context.database) {
        when (notification) {
            is NewRequest -> {
                val ids = listOf(notification.fromId)
                val from = UsersService
                    .impureDetails(context, ids)
                    .first() ?: error("User is required to be found")
                NotificationDetails.NewRequest(from, notification.isMutual)
            }
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
