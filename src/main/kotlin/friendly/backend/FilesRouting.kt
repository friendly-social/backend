package friendly.backend

import java.nio.file.Paths
import io.ktor.server.util.getValue
import kotlinx.io.buffered
import io.ktor.http.ContentDisposition
import io.ktor.utils.io.asSource
import kotlinx.coroutines.flow.toList
import io.ktor.http.content.asFlow
import io.ktor.http.content.PartData
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondPath
import io.ktor.server.request.receiveMultipart
import kotlinx.serialization.Serializable

object FilesRouting {
    @Serializable
    private data class UploadResponse(
        val id: FileIdSerializable,
        val accessHash: FileAccessHashSerializable,
    )

    fun impureUpload(context: AppContext) {
        context.routing.post("/files/upload") {
            val cloudflareBasedIP = call.request.headers["CF-Connecting-IP"]

            if (cloudflareBasedIP == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "No access to lava lamps",
                )
                return@post
            }

            val multipart = call.receiveMultipart()
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
                val channel = file.provider().asSource()

                val sizeMetadata = file.contentDisposition
                    ?.parameter(ContentDisposition.Parameters.Size)
                    ?.toLong()
                    ?.let(FileSize::orThrow)

                val result = FilesService.impureUpload(
                    context = context,
                    ip = IpAddress.orThrow(cloudflareBasedIP),
                    sizeMetadata = sizeMetadata,
                    source = channel.buffered(),
                )

                when (result) {
                    is FilesService.UploadResult.Success -> {
                        call.respond(result.serializable())
                    }
                    is FilesService.UploadResult.InsufficentStorage -> {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = "Insufficient Storage",
                        )
                    }
                }
            } finally {
                file.dispose()
            }
        }
    }

    private fun FilesService.UploadResult.Success.serializable(): UploadResponse {
        return UploadResponse(id.serializable(), accessHash.serializable())
    }

    fun impureDownload(context: AppContext) {
        context.routing.get("/files/download/{idLong}/{accessHashString}") {
            val idLong: Long by call.pathParameters
            val accessHashString: String by call.pathParameters

            val id = FileIdSerializable(idLong)
            val accessHash = FileAccessHashSerializable(accessHashString)

            val result = FilesService.impureGetPath(
                context = context,
                id = id.typed(),
                accessHash = accessHash.typed(),
            )

            when (result) {
                is FilesService.GetPathResult.NotFound -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                is FilesService.GetPathResult.Success -> {
                    val javaPath = Paths.get(result.path.toString())
                    call.respondPath(javaPath)
                }
            }

        }
    }
}
