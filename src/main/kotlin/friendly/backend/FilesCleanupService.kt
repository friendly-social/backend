package friendly.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FilesCleanupService")

object FilesCleanupService {
    fun attach(context: AppContext) {
        context.scope.launch {
            while (true) {
                doCleanupCatching(context)
                delay(context.files.cleanupInterval)
            }
        }
    }

    suspend fun doCleanupCatching(context: AppContext) {
        runCatching {
            doCleanup(context)
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            logger.error("Error during cleanup", throwable)
        }
    }

    suspend fun doCleanup(context: AppContext) {
        markForDeletion(context)
        if (runIO(context)) {
            suspendTransaction { FilesTable.deleteMarked() }
        }
    }

    // TODO: while marking, everything happens in HIGH TRANSACTION ISOLATION
    //    (meaning if what I read changed == ROLLBACK,
    //    possibly deleted wrong thing)
    // Also take chunks into account, so we can't just isolate everything
    suspend fun markForDeletion(context: AppContext): Unit =
        suspendTransaction {
            val instant = context.clock.now() - context.files.cleanupDelay
            FilesTable
                .selectBefore(instant)
                .chunked(1_000)
                .collect { files ->
                    logger.info("Check for deletion: $files")
                    val filtered = files.toSet().unusedAsAvatars().toList()
                    logger.info("Mark for deletion: $filtered")
                    FilesTable.markForDeletion(filtered)
                }
        }

    suspend fun Set<FileId>.unusedAsAvatars(): Set<FileId> =
        this - UsersTable.usedAsAvatars(this)

    suspend fun runIO(context: AppContext): Boolean = suspendTransaction {
        try {
            FilesTable
                .selectForDeletion()
                .chunked(1_000)
                .collect { files ->
                    logger.info("Deleting: $files")
                    withContext(Dispatchers.IO) {
                        for (id in files) {
                            val path = Path(
                                base = context.files.directory,
                                "${id.long}",
                            )
                            context.files.fileSystem
                                .delete(path, mustExist = false)
                        }
                    }
                }
            logger.info("Successfully deleted")
            true
        } catch (cause: Exception) {
            if (cause is CancellationException) {
                throw cause
            }
            // No-op. We will try to delete in again on the next scan
            logger.error("Can't delete files", cause)
            false
        }
    }
}
