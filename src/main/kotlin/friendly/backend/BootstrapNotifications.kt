package friendly.backend

import me.y9san9.aqueue.AQueue
import me.y9san9.graceful.gracefulScope

suspend inline fun bootstrapNotifications(
    crossinline block: suspend (NotificationsContext) -> Unit,
) {
    val queue = AQueue()
    gracefulScope { scope ->
        val context = NotificationsContext(scope, queue)
        block(context)
    }
}
