package friendly.backend

import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.util.getValue

object FeedRouting {
    fun queue(context: AppContext) {
        context.routing.get("/feed/queue") {
            val authorization = call.authorizationOrThrow()
            val result = FeedService.queue(context, authorization)
            when (result) {
                is Unauthorized -> call.respondAuthorizationInvalid()
                is Success -> call.respond(result.details.serializable())
            }
        }
    }
}
