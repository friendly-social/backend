package friendly.backend

import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration

data class FilesContext(
    val cleanupInterval: Duration,
    val cleanupDelay: Duration,
) {
    val preuploadCleanupTrigger: Channel<Unit> = Channel(Channel.CONFLATED)
}
