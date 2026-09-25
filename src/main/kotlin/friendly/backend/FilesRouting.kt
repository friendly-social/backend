package friendly.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getValue
import io.ktor.utils.io.ByteReadChannel

object FilesRouting {
    fun preupload(context: AppContext) {
        context.routing.post("/files/preupload") {
            val multipart = call.receiveMultipart()

            val sizeMetadata = call
                .request.headers["X-File-Size"]
                ?.toLongOrNull()
                ?.takeIf { long -> long >= 0 }
                ?.let(FileSize::orThrow)

            if (sizeMetadata == null) {
                @Suppress("ktlint:standard:max-line-length")
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "File size is required in advance to check for available space",
                )
                return@post
            }

            val file = multipart.readPart()

            if (file == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Expecting a single file part",
                )
                return@post
            }

            if (file !is PartData.FileItem) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Expecting a single file part",
                )
                return@post
            }

            try {
                val channel = file.provider()

                val result = FilesService.preupload(
                    context = context,
                    sizeMetadata = sizeMetadata,
                    source = channel,
                )

                when (result) {
                    is Fail -> {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                    }
                    is InsufficientStorage -> {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = "Insufficient Storage",
                        )
                    }
                    is Ok -> {
                        call.respond(result.descriptor.serializable())
                    }
                }
            } finally {
                file.dispose()
            }
        }
    }

    fun upload(context: AppContext) {
        context.routing.post("/files/upload") {
            val authorization = call.authorization()
            val multipart = call.receiveMultipart()

            val sizeMetadata = call
                .request.headers["X-File-Size"]
                ?.toLongOrNull()
                ?.takeIf { long -> long >= 0 }
                ?.let(FileSize::orThrow)

            if (sizeMetadata == null) {
                @Suppress("ktlint:standard:max-line-length")
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "File size is required in advance to check for available space",
                )
                return@post
            }

            val file = multipart.readPart()

            if (file == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Expecting a single file part",
                )
                return@post
            }

            if (file !is PartData.FileItem) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Expecting a single file part",
                )
                return@post
            }

            try {
                val channel = file.provider()

                val result = FilesService.upload(
                    context = context,
                    authorization = authorization,
                    sizeMetadata = sizeMetadata,
                    source = channel,
                )

                when (result) {
                    is Fail -> {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                    }
                    is Unauthorized -> {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                    is InsufficientStorage -> {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = "Insufficient Storage",
                        )
                    }
                    is Ok -> {
                        call.respond(result.descriptor.serializable())
                    }
                }
            } finally {
                file.dispose()
            }
        }
    }

    fun download(context: AppContext) {
        context.routing.get("/files/download/{id}/{accessHash}") {
            val id = call.fileId("id")
            val accessHash = call.fileAccessHash("accessHash")

            val result = FilesService.download(
                context = context,
                id = id,
                accessHash = accessHash,
            )

            when (result) {
                is FilesService.DownloadResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Unauthorized)
                }
                is FilesService.DownloadResult.NotFound -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                is FilesService.DownloadResult.Ok -> {
                    call.response.header(
                        HttpHeaders.CacheControl,
                        "public, max-age=31536000, immutable",
                    )
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val contentType =
                                ContentType.Application.OctetStream
                            override val contentLength =
                                result.size.bytes
                            override fun readFrom(): ByteReadChannel =
                                result.channel
                        },
                    )
                }
            }
        }
    }
}
