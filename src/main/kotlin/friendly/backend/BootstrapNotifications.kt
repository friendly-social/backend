package friendly.backend

import me.y9san9.aqueue.AQueue

suspend fun impureBootstrapNotifications(): NotificationsContext =
    NotificationsContext(queue = AQueue())
