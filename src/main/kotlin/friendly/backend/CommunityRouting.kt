package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

object CommunityRouting {
    @Serializable
    data class PostBody(val text: CommunityPostTextSerializable)

    fun post(context: AppContext) {
        context.routing.post("/community") {
            val authorization = call.authorization()
            val body = call.receive<PostBody>()
            val result = CommunityService.post(
                context = context,
                authorization = authorization,
                text = body.text.typed(),
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is Success -> call.respond(HttpStatusCode.OK)
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
}
