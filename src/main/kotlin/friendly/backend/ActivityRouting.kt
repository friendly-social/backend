package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get

object ActivityRouting {
    fun list(context: AppContext) {
        context.routing.get("/activity/list/{cursorId?}") {
            val authorization = call.authorization()
            val cursorId = call.cursorIdOrNull("cursorId")
            val result = ActivityService.list(
                context = context,
                authorization = authorization,
                cursorId = cursorId,
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is CursorInvalid -> call.respond(HttpStatusCode.BadRequest)
                is Success -> {
                    val response = result.cursor
                        .serializable { details -> details.serializable() }
                    call.respond(response)
                }
            }
        }
    }
}
