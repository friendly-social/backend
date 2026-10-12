package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

object ActivityRouting {
    fun listCompatible(context: AppContext) {
        context.routing.get("/activity/list/{cursorId?}") {
            val compatible = call.compatible()
            when {
                compatible.int >= 2 -> list(context, call)
                else -> list1(context, call)
            }
        }
    }

    @Serializable
    data class ListResponse(
        val hasUnread: Boolean,
        val cursor: CursorSerializable<ActivityDetailsSerializable>,
    )

    suspend fun list(context: AppContext, call: RoutingCall) {
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
            is Success -> call.respond(result.serializable())
        }
    }

    private fun ActivityService.ListResult.Success.serializable() =
        ListResponse(
            hasUnread = hasUnread,
            cursor = cursor.serializable { details -> details.serializable() },
        )

    suspend fun list1(context: AppContext, call: RoutingCall) {
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
