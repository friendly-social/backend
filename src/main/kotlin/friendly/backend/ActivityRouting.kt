package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post

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

    fun read(context: AppContext) {
        context.routing.post("/activity/read/{id}") {
            val authorization = call.authorization()
            val id = call.activityId("id")
            val result = ActivityService.read(context, authorization, id)
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }
}
