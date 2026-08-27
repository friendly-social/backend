package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get

object NotificationsRouting {
    fun details(context: AppContext) {
        context.routing.get("/notifications/details/{id}") {
            val authorization = call.authorization()
            val id = call.notificationId("id")
            val result = NotificationsService.details(
                context = context,
                authorization = authorization,
                id = id,
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(result.details.serializable())
            }
        }
    }
}
