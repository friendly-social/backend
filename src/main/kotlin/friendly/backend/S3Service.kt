package friendly.backend

import aws.sdk.kotlin.services.s3.copyObject
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger("S3Service")

object S3Service {
    sealed interface UploadResult {
        data object InsufficientStorage : UploadResult
        data object Fail : UploadResult
        data object Ok : UploadResult
    }

    suspend fun upload(
        context: AppContext,
        source: ByteReadChannel,
        sizeMetadata: FileSize,
        fileId: FilePreuploadId,
    ): UploadResult = upload(
        context = context,
        source = source,
        sizeMetadata = sizeMetadata,
        fileIdLong = fileId.long,
        preupload = true,
    )

    suspend fun upload(
        context: AppContext,
        source: ByteReadChannel,
        sizeMetadata: FileSize,
        fileId: FileId,
    ): UploadResult = upload(
        context = context,
        source = source,
        sizeMetadata = sizeMetadata,
        fileIdLong = fileId.long,
        preupload = false,
    )

    private suspend fun upload(
        context: AppContext,
        source: ByteReadChannel,
        sizeMetadata: FileSize,
        fileIdLong: Long,
        preupload: Boolean,
    ): UploadResult {
        val friendlyBucket = if (preupload) {
            context.s3.friendlyPreuploadBucket
        } else {
            context.s3.friendlyBucket
        }
        val uploadUrl = context.s3.sdk.presignPutObject(
            input = PutObjectRequest {
                bucket = friendlyBucket
                key = "$fileIdLong"
            },
            duration = 15.minutes,
        ).url
        val response = context.s3.httpClient.put(uploadUrl.toString()) {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentType = ContentType.Application.OctetStream
                override val contentLength = sizeMetadata.bytes
                override fun readFrom() = source
            })
        }
        return when (response.status) {
            OK -> UploadResult.Ok
            PayloadTooLarge -> UploadResult.InsufficientStorage
            else -> {
                logger.error("Cannot upload file to S3")
                logger.error(response.bodyAsText())
                UploadResult.Fail
            }
        }
    }

    sealed interface DownloadResult {
        data object Fail : DownloadResult
        class Ok(val channel: ByteReadChannel) : DownloadResult
    }

    suspend fun download(context: AppContext, fileId: FileId): DownloadResult {
        val downloadUrl = context.s3.sdk.presignGetObject(
            input = GetObjectRequest {
                bucket = context.s3.friendlyBucket
                key = fileId.long.toString()
            },
            duration = 15.minutes,
        ).url
        val response = context.s3.httpClient.get(downloadUrl.toString())
        if (response.status != OK) {
            logger.error("Cannot download ${fileId.long} from S3")
            logger.error(response.bodyAsText())
            return Fail
        }
        val channel = response.bodyAsChannel()
        return DownloadResult.Ok(channel)
    }

    @JvmName("deletePreupload")
    suspend fun delete(
        context: AppContext,
        ids: List<FilePreuploadId>,
    ): DeleteResult = delete(
        context = context,
        idsLong = ids.map { id -> id.long },
        preupload = true,
    )

    suspend fun delete(context: AppContext, ids: List<FileId>): DeleteResult =
        delete(
            context = context,
            idsLong = ids.map { id -> id.long },
            preupload = false,
        )

    sealed interface DeleteResult {
        fun orThrow()

        data object Fail : DeleteResult {
            override fun orThrow(): Nothing = error("$this")
        }
        data object Ok : DeleteResult {
            override fun orThrow() {}
        }
    }

    private suspend fun delete(
        context: AppContext,
        idsLong: List<Long>,
        preupload: Boolean,
    ): DeleteResult {
        val friendlyBucket = if (preupload) {
            context.s3.friendlyPreuploadBucket
        } else {
            context.s3.friendlyBucket
        }
        return try {
            context.s3.sdk.deleteObjects {
                bucket = friendlyBucket
                delete = Delete {
                    objects = idsLong.map { id ->
                        ObjectIdentifier {
                            key = "$id"
                        }
                    }
                }
            }
            DeleteResult.Ok
        } catch (exception: RuntimeException) {
            coroutineContext.ensureActive()
            logger.error("Couldn't delete $idsLong", exception)
            DeleteResult.Fail
        }
    }

    sealed interface MovePreuploadResult {
        fun orThrow()
        data object Fail : MovePreuploadResult {
            override fun orThrow(): Nothing = error("$this")
        }
        data object Ok : MovePreuploadResult {
            override fun orThrow() {}
        }
    }

    suspend fun movePreupload(
        context: AppContext,
        from: FilePreuploadId,
        to: FileId,
    ): MovePreuploadResult {
        val fromKey = "${from.long}"
        val toKey = "${to.long}"

        val fromBucket = context.s3.friendlyPreuploadBucket
        val toBucket = context.s3.friendlyBucket

        val copiedBefore: Boolean = try {
            context.s3.sdk.headObject {
                bucket = toBucket
                key = toKey
            }
            true
        } catch (_: NotFound) {
            false
        }

        if (!copiedBefore) {
            try {
                context.s3.sdk.copyObject {
                    bucket = toBucket
                    key = toKey
                    copySource = "$fromBucket/$fromKey"
                }
            } catch (exception: RuntimeException) {
                coroutineContext.ensureActive()
                logger.error("Couldn't delete while moving", exception)
                return MovePreuploadResult.Fail
            }
        }

        val deletedBefore: Boolean = try {
            context.s3.sdk.headObject {
                bucket = fromBucket
                key = fromKey
            }
            false
        } catch (_: NotFound) {
            true
        }

        if (!deletedBefore) {
            try {
                context.s3.sdk.deleteObject {
                    bucket = fromBucket
                    key = fromKey
                }
            } catch (exception: RuntimeException) {
                coroutineContext.ensureActive()
                logger.error("Couldn't delete while moving", exception)
                return MovePreuploadResult.Fail
            }
        }

        return MovePreuploadResult.Ok
    }
}
