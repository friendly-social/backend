package friendly.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FilesCleanupService")

object FilesCleanupService {
    fun attach(context: AppContext) {
        context.scope.launch {
            suspendTransaction(context.database) { FilesTable.deletePending() }
            while (true) {
                doCleanupCatching(context)
                delay(context.files.cleanupInterval)
            }
        }
        attachPreupload(context)
    }

    suspend fun doCleanupCatching(context: AppContext) {
        try {
            doCleanup(context)
        } catch (exception: RuntimeException) {
            if (exception is CancellationException) {
                throw exception
            }
            logger.error("Error during cleanup", exception)
        }
    }

    suspend fun doCleanup(context: AppContext) {
        markForDeletion(context)
        suspendTransaction(context.database) {
            FilesTable
                .selectForDeletion()
                .chunked(1_000)
                .collect { filesToDelete ->
                    S3Service.delete(
                        context = context,
                        ids = filesToDelete,
                    ).orThrow()
                }
            FilesTable.deleteMarkedForDeletion()
        }
    }

    suspend fun markForDeletion(context: AppContext) {
        // no-op
    }

    /**
     * This implementation is frozen for future times when we will have
     * entities and markdown, so it is possible to query what images are used in
     * posts and what are not
     */
    suspend fun markForDeletionFrozen(context: AppContext): Unit =
        suspendTransaction(
            db = context.database,
            transactionIsolation = SERIALIZABLE,
        ) {
            val instant = context.clock.now() - context.files.cleanupDelay
            FilesTable
                .selectBefore(instant, pending = false, markForDeletion = false)
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

    fun triggerPreuploadCleanup(context: AppContext) {
        context.files.preuploadCleanupTrigger.trySend(Unit)
    }

    fun attachPreupload(context: AppContext) {
        context.scope.launch {
            suspendTransaction(context.database) {
                FilesPreuploadTable.deletePending()
            }
            for (trigger in context.files.preuploadCleanupTrigger) {
                try {
                    doPreuploadCleanup(context)
                } catch (exception: RuntimeException) {
                    if (exception is CancellationException) {
                        throw exception
                    }
                    logger.error("Failed onPreuploadCleanupTrigger", exception)
                }
            }
        }
        triggerPreuploadCleanup(context)
    }

    suspend fun doPreuploadCleanup(context: AppContext) {
        suspendTransaction(context.database) {
            FilesPreuploadTable.selectForDeletion()
                .chunked(1000)
                .collect { filesToDelete ->
                    S3Service.delete(
                        context = context,
                        ids = filesToDelete,
                    )
                }
            FilesPreuploadTable.deleteMarkedForDeletion()
        }
    }
}
