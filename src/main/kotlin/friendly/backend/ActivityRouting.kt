package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    @Serializable
    sealed interface NewUnreadsResponse {
        @Serializable
        @SerialName("none")
        data object None : NewUnreadsResponse

        @Serializable
        @SerialName("some")
        data class Some(val activityId: ActivityIdSerializable) :
            NewUnreadsResponse
    }

    fun newUnreads(context: AppContext) {
        context.routing.get("/activity/unreads/new") {
            val authorization = call.authorization()
            val result = ActivityService.newUnreads(context, authorization)
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is None -> call.respond<NewUnreadsResponse>(
                    NewUnreadsResponse.None,
                )
                is Some -> {
                    val activityId = result.activityId.serializable()
                    val response = NewUnreadsResponse.Some(activityId)
                    call.respond<NewUnreadsResponse>(response)
                }
            }
        }
    }

    fun acknowledgeUnreads(context: AppContext) {
        context.routing.post("/activity/unreads/acknowledge") {
            val authorization = call.authorization()
            val result = ActivityService.acknowledgeUnreads(
                context = context,
                authorization = authorization,
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is Ok -> call.respond(HttpStatusCode.OK)
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
