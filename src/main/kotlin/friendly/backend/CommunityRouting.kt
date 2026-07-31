package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

object CommunityRouting {
    @Serializable
    data class PostBody(
        val text: CommunityPostTextSerializable,
        val replyTo: CommunityPostDescriptorSerializable? = null,
    )

    fun post(context: AppContext) {
        context.routing.post("/community") {
            val authorization = call.authorization()
            val body = call.receive<PostBody>()
            val result = CommunityService.post(
                context = context,
                authorization = authorization,
                text = body.text.typed(),
                replyTo = body.replyTo?.typed(),
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(result.descriptor.serializable())
            }
        }
    }

    fun list(context: AppContext) {
        context.routing.get("/community/list/{cursorId?}") {
            val authorization = call.authorization()
            val cursorId = call.cursorIdOrNull("cursorId")
            val result = CommunityService.list(context, authorization, cursorId)
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is CursorInvalid -> call.respond(HttpStatusCode.BadRequest)
                is Success -> {
                    val response = result.cursor
                        .serializable { post -> post.serializable() }
                    call.respond(response)
                }
            }
        }
    }

    fun replies(context: AppContext) {
        context.routing.get(
            "/community/{id}/{accessHash}/replies/{cursorId?}",
        ) {
            val authorization = call.authorization()
            val id = call.postId("id")
            val accessHash = call.postAccessHash("accessHash")
            val cursorId = call.cursorIdOrNull("cursorId")
            val result = CommunityService.replies(
                context = context,
                authorization = authorization,
                replyTo = CommunityPostDescriptor(id, accessHash),
                cursorId = cursorId,
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is CursorInvalid -> call.respond(HttpStatusCode.BadRequest)
                is Success -> {
                    val response = result.cursor
                        .serializable { post -> post.serializable() }
                    call.respond(response)
                }
            }
        }
    }

    @Serializable
    data class EditBody(
        val text: FieldSerializable<CommunityPostTextSerializable>?,
    )

    fun edit(context: AppContext) {
        context.routing.post("/community/{id}/edit") {
            val authorization = call.authorization()
            val id = call.postId("id")
            val body = call.receive<EditBody>()
            val result = CommunityService.edit(
                context = context,
                authorization = authorization,
                id = id,
                text = body.text?.typed { value -> value.typed() },
            )
            when (result) {
                Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                NotFound -> call.respond(HttpStatusCode.NotFound)
                Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }

    fun delete(context: AppContext) {
        context.routing.post("/community/{id}/delete") {
            val authorization = call.authorization()
            val id = call.postId("id")
            val result = CommunityService.delete(
                context = context,
                authorization = authorization,
                id = id,
            )
            when (result) {
                Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                NotFound -> call.respond(HttpStatusCode.NotFound)
                Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }
}
