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

    // todo: pagination support
    fun list(context: AppContext) {
        context.routing.get("/community/list") {
            val authorization = call.authorization()
            val result = CommunityService.list(context, authorization)
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is Success -> {
                    val response = result.list.map { post ->
                        post.serializable()
                    }
                    call.respond(response)
                }
            }
        }
    }
}
