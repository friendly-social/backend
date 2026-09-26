package friendly.backend

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object FilesService {
    val SERVICE_SIZE_OVERALL = 9L.GB
    val SERVICE_SIZE_PER_DAY = 1L.GB

    const val SERVICE_DOWNLOAD_PER_DAY = 300_000
    const val SERVICE_UPLOAD_PER_DAY = 30_000

    val USER_SIZE_PER_DAY = 128L.MB
    const val USER_UPLOAD_PER_DAY = 3_000

    val FILE_SIZE = 20L.MB

    val PREUPLOAD_SIZE_OVERALL = 50L.MB
    val PREUPLOAD_FILE_SIZE = 5L.MB

    sealed interface PreuploadResult {
        data object InsufficientStorage : PreuploadResult
        data object Fail : PreuploadResult
        data class Ok(val descriptor: FilePreuploadDescriptor) : PreuploadResult
    }

    suspend fun preupload(
        context: AppContext,
        sizeMetadata: FileSize,
        source: ByteReadChannel,
    ): PreuploadResult {
        if (sizeMetadata > PREUPLOAD_FILE_SIZE) {
            return InsufficientStorage
        }
        return suspendTransaction(context.database) tx@{
            val instant = context.clock.now()
            val accessHash = FilePreuploadAccessHash.random(context.random)
            val reserveResult = reservePreuploadFile(
                context = context,
                size = sizeMetadata,
                instant = instant,
                accessHash = accessHash,
            )
            val id = when (reserveResult) {
                is InsufficientStorage -> return@tx Fail
                is Ok -> reserveResult.id
            }
            val descriptor = FilePreuploadDescriptor(id, accessHash)
            try {
                val result = S3Service.upload(
                    context = context,
                    source = source,
                    sizeMetadata = sizeMetadata,
                    fileId = id,
                )
                when (result) {
                    InsufficientStorage ->
                        PreuploadResult.InsufficientStorage
                    Fail ->
                        PreuploadResult.Fail
                    Ok -> {
                        FilesPreuploadTable.complete(id)
                        PreuploadResult.Ok(descriptor)
                    }
                }
            } catch (exception: Exception) {
                withContext(NonCancellable) {
                    FilesPreuploadTable.deleteById(id)
                }
                throw exception
            }
        }
    }

    sealed interface ReservePreuploadFileResult {
        /*
         * This is possible if too many parallel anonymous file uploads happen
         * at the same time and all files are pending, so we have nothing to
         * clean.
         */
        data object InsufficientStorage : ReservePreuploadFileResult
        data class Ok(val id: FilePreuploadId) : ReservePreuploadFileResult
    }

    /**
     * Atomic action to reserve space.
     */
    private suspend fun reservePreuploadFile(
        context: AppContext,
        size: FileSize,
        instant: Instant,
        accessHash: FilePreuploadAccessHash,
    ): ReservePreuploadFileResult = suspendTransaction(
        db = context.database,
        transactionIsolation = SERIALIZABLE,
    ) tx@{
        maxAttempts = Int.MAX_VALUE
        val totalSize = FilesPreuploadTable.selectFilesSize(
            pending = null,
            markForDeletion = false,
        )
        val freeSpace = PREUPLOAD_SIZE_OVERALL.bytes - totalSize.bytes
        val cleanSpace = size.bytes - freeSpace
        if (cleanSpace > 0) {
            var cumulativeSize = 0L
            val filesToDelete = FilesPreuploadTable.selectOldFiles(
                pending = false,
                markForDeletion = false,
            )
                .transformWhile { entry ->
                    emit(entry)
                    cumulativeSize += entry.size.bytes
                    cumulativeSize < cleanSpace
                }
                .toList()
            val hasEnoughSpace = filesToDelete
                .sumOf { entry -> entry.size.bytes } >= cleanSpace
            if (!hasEnoughSpace) {
                return@tx ReservePreuploadFileResult.InsufficientStorage
            }
            FilesPreuploadTable.markForDeletion(
                ids = filesToDelete.map { entry -> entry.id },
            )
            FilesCleanupService.triggerPreuploadCleanup(context)
        }
        val id = FilesPreuploadTable.insert(size, instant, accessHash)
        ReservePreuploadFileResult.Ok(id)
    }

    sealed interface PreuploadCompleteResult {
        data object NotFound : PreuploadCompleteResult
        data class Ok(val descriptor: FileDescriptor) : PreuploadCompleteResult
    }

    suspend fun preuploadComplete(
        context: AppContext,
        ownerId: UserId,
        descriptor: FilePreuploadDescriptor,
    ): PreuploadCompleteResult = suspendTransaction(context.database) tx@{
        val entry = FilesPreuploadTable.selectByIdOrNull(
            id = descriptor.id,
            pending = false,
            markForDeletion = false,
        ) ?: return@tx PreuploadCompleteResult.NotFound
        if (entry.accessHash != descriptor.accessHash) {
            return@tx PreuploadCompleteResult.NotFound
        }
        val resultAccessHash = FileAccessHash.orThrow(
            entry.accessHash.string,
        )
        val id = FilesTable.insert(
            ownerId = ownerId,
            size = entry.size,
            instant = entry.instant,
            accessHash = resultAccessHash,
            pending = false,
        )
        S3Service.movePreupload(
            context = context,
            from = descriptor.id,
            to = id,
        ).orThrow()
        FilesPreuploadTable.deleteById(descriptor.id)
        PreuploadCompleteResult.Ok(
            descriptor = FileDescriptor(id, resultAccessHash),
        )
    }

    sealed interface UploadResult {
        data object InsufficientStorage : UploadResult
        data object Unauthorized : UploadResult
        data object Fail : UploadResult
        data class Ok(val descriptor: FileDescriptor) : UploadResult
    }

    suspend fun upload(
        context: AppContext,
        authorization: Authorization,
        sizeMetadata: FileSize,
        source: ByteReadChannel,
    ): UploadResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return Unauthorized }
        return suspendTransaction(context.database) tx@{
            val instant = context.clock.now()
            val accessHash = FileAccessHash.random(context.random)
            val reserveResult = reserveFile(
                context = context,
                ownerId = authorization.id,
                size = sizeMetadata,
                instant = instant,
                accessHash = accessHash,
            )
            val id = when (reserveResult) {
                is InsufficientStorage -> {
                    sendInsufficientStorageAlert(
                        context = context,
                        reason = reserveResult,
                        instant = instant,
                        userId = authorization.id,
                    )
                    return@tx UploadResult.InsufficientStorage
                }
                is Ok -> reserveResult.id
            }
            val descriptor = FileDescriptor(id, accessHash)
            try {
                val result = S3Service.upload(
                    context = context,
                    source = source,
                    sizeMetadata = sizeMetadata,
                    fileId = id,
                )
                when (result) {
                    InsufficientStorage ->
                        UploadResult.InsufficientStorage
                    Fail ->
                        UploadResult.Fail
                    Ok -> {
                        FilesTable.complete(id)
                        UploadResult.Ok(descriptor)
                    }
                }
            } catch (exception: Exception) {
                withContext(NonCancellable) {
                    FilesTable.deleteById(id)
                }
                throw exception
            }
        }
    }

    sealed interface ReserveFileResult {
        sealed interface InsufficientStorage : ReserveFileResult {
            data object File : InsufficientStorage
            data object UserPerDay : InsufficientStorage
            data object ServicePerDay : InsufficientStorage
            data object ServiceOverall : InsufficientStorage
        }
        data class Ok(val id: FileId) : ReserveFileResult
    }

    /**
     * Atomic action to reserve space.
     */
    private suspend fun reserveFile(
        context: AppContext,
        ownerId: UserId,
        size: FileSize,
        instant: Instant,
        accessHash: FileAccessHash,
    ): ReserveFileResult = suspendTransaction(
        db = context.database,
        transactionIsolation = SERIALIZABLE,
    ) {
        maxAttempts = Int.MAX_VALUE
        val insufficient = checkRequestedSize(ownerId, instant, size)
        if (insufficient == null) {
            val id = FilesTable.insert(
                ownerId = ownerId,
                size = size,
                instant = instant,
                accessHash = accessHash,
                pending = true,
            )
            ReserveFileResult.Ok(id)
        } else {
            insufficient
        }
    }

    private suspend fun checkRequestedSize(
        ownerId: UserId,
        instant: Instant,
        size: FileSize,
    ): ReserveFileResult.InsufficientStorage? {
        if (size > FILE_SIZE) {
            return File
        }
        val userPerDayOccupied = FilesTable.selectFilesSize(
            ownerId = ownerId,
            after = instant - 1.days,
            pending = null,
            markForDeletion = false,
        )
        val userPerDayQuota = USER_SIZE_PER_DAY.minusOrZero(userPerDayOccupied)
        if (userPerDayQuota < size) {
            return UserPerDay
        }
        val servicePerDayOccupied = FilesTable.selectFilesSize(
            ownerId = null,
            after = instant - 1.days,
            pending = null,
            markForDeletion = false,
        )
        val servicePerDayQuota = SERVICE_SIZE_PER_DAY.minusOrZero(
            servicePerDayOccupied,
        )
        if (servicePerDayQuota < size) {
            return ServicePerDay
        }
        val serviceOverallOccupied = FilesTable.selectFilesSize(
            ownerId = null,
            after = null,
            pending = null,
            markForDeletion = false,
        )
        val serviceOverallQuota = SERVICE_SIZE_OVERALL.minusOrZero(
            serviceOverallOccupied,
        )
        if (serviceOverallQuota < size) {
            return ServiceOverall
        }
        return null
    }

    private suspend fun sendInsufficientStorageAlert(
        context: AppContext,
        reason: ReserveFileResult.InsufficientStorage,
        instant: Instant,
        userId: UserId,
    ) {
        val payload: AlertPayload = when (reason) {
            File -> return
            UserPerDay ->
                AlertPayload.Limit.FilesUserSizePerDay(instant, userId)
            ServicePerDay ->
                AlertPayload.Limit.FilesServiceSizePerDay(instant)
            ServiceOverall ->
                AlertPayload.Limit.FilesServiceSizeOverall(instant)
        }
        AlertsService.post(context, payload)
    }

    sealed interface DownloadResult {
        data object Unauthorized : DownloadResult
        data object NotFound : DownloadResult
        class Ok(val size: FileSize, val channel: ByteReadChannel) :
            DownloadResult
    }

    suspend fun download(
        context: AppContext,
        id: FileId,
        accessHash: FileAccessHash,
    ): DownloadResult {
        val entry = suspendTransaction(context.database) {
            FilesTable.selectByIdOrNull(
                id = id,
                pending = false,
                markForDeletion = false,
            )
        } ?: return NotFound
        if (entry.accessHash != accessHash) {
            return NotFound
        }
        val result = S3Service.download(
            context = context,
            fileId = id,
        )
        return when (result) {
            is Fail -> NotFound
            is Ok -> DownloadResult.Ok(entry.size, result.channel)
        }
    }
}
