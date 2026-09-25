package friendly.backend

import kotlin.time.Duration.Companion.days

fun bootstrapFiles(): FilesContext = FilesContext(
    cleanupInterval = 1.days,
    cleanupDelay = 7.days,
)
