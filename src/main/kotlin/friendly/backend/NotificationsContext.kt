package friendly.backend

import me.y9san9.aqueue.AQueue
import me.y9san9.graceful.GracefulScope

data class NotificationsContext(val scope: GracefulScope, val queue: AQueue)
