package friendly.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.Source
import kotlinx.io.files.Path
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object FilesService {
    sealed interface UploadResult {
        data object InsufficentStorage : UploadResult

        data class Success(val id: FileId, val accessHash: FileAccessHash) :
            UploadResult
    }

    suspend fun upload(
        context: AppContext,
        ip: IpAddress,
        sizeMetadata: FileSize?,
        source: Source,
    ): UploadResult = suspendTransaction(context.database) {
        val availableSize = getAvailableSize(context, ip)
        if (sizeMetadata != null && sizeMetadata > availableSize) {
            UploadResult.InsufficentStorage
        } else {
            val fileId = FilesTable.insert()
            val path = Path(context.files.directory, "${fileId.long}")
            val measuredSize = streamToFile(
                context = context,
                path = path,
                source = source,
                availableSize = availableSize,
            )
            if (measuredSize == null) {
                UploadResult.InsufficentStorage
            } else {
                val instant = context.clock.now()
                val accessHash = FileAccessHash.random(context.random)
                FilesTable.update(
                    id = fileId,
                    instant = instant,
                    accessHash = accessHash,
                    size = measuredSize,
                    ownerIp = ip,
                )
                UploadResult.Success(fileId, accessHash)
            }
        }
    }

    private suspend fun getAvailableSize(
        context: AppContext,
        ip: IpAddress,
    ): FileSize = suspendTransaction(context.database) {
        val userOccupied = FilesTable.selectFilesSize(ip)
        val userAvailable = FileSize.MaxPerIp.minusOrZero(userOccupied)

        val maxDirectorySize = context.files.maxDirectorySize
        val systemOccupied = FilesTable.selectFilesSize()
        val systemAvailable = maxDirectorySize.minusOrZero(systemOccupied)

        userAvailable.coerceAtMost(systemAvailable)
    }

    private suspend fun streamToFile(
        context: AppContext,
        path: Path,
        source: Source,
        availableSize: FileSize,
    ): FileSize? = withContext(Dispatchers.IO) {
        blockingStreamToFile(context, path, source, availableSize)
    }

    /**
     * @return measured actual file size or null if it didn't fit to limits
     */
    private fun blockingStreamToFile(
        context: AppContext,
        path: Path,
        source: Source,
        availableSize: FileSize,
    ): FileSize? {
        val sink = context.files.fileSystem.sink(path)

        fun revert() {
            context.files.fileSystem.delete(path)
        }

        try {
            val read = source
                .blockingReadAtMostTo(sink, availableSize.bytes)
            if (source.exhausted()) {
                return FileSize.orThrow(read)
            } else {
                revert()
                return null
            }
        } catch (throwable: Throwable) {
            revert()
            throw throwable
        }
    }

    private fun Source.blockingReadAtMostTo(sink: RawSink, bytes: Long): Long {
        require(bytes > 0)
        var remaining = bytes
        val buffer = Buffer()

        fun flush() {
            sink.flush()
        }

        while (!this.exhausted()) {
            if (remaining <= 0) {
                flush()
                return bytes
            }
            val chunkSize = remaining.coerceAtMost(DEFAULT_BUFFER_SIZE.toLong())
            val read = this.readAtMostTo(buffer, chunkSize)
            sink.write(buffer, read)
            remaining -= read
        }

        flush()
        return bytes - remaining
    }

    sealed interface GetPathResult {
        data object NotFound : GetPathResult
        data class Success(val path: Path) : GetPathResult
    }

    suspend fun getPath(
        context: AppContext,
        id: FileId,
        accessHash: FileAccessHash,
    ): GetPathResult = suspendTransaction(context.database) {
        val storedAccessHash = FilesTable.selectAccessHash(id)
        if (accessHash != storedAccessHash) {
            GetPathResult.NotFound
        } else {
            val path = Path(
                base = context.files.directory,
                "${id.long}",
            )
            GetPathResult.Success(path)
        }
    }
}
