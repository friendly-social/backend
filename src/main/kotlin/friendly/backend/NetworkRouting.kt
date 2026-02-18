package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get

object NetworkRouting {
    fun details(context: AppContext) {
        context.routing.get("/network/details") {
            val authorization = call.authorization()
            val result = NetworkService.details(context, authorization)
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is Success -> call.respond(result.details.serializable())
            }
        }
    }
}
