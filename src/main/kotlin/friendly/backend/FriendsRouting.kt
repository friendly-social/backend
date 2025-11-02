package friendly.backend

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object FriendsRouting {

    @Serializable
    private data class GenerateResponse(val token: FriendTokenSerializable)

    fun impureGenerate(context: AppContext) {
        context.routing.post("/friends/generate") {
            val authorization = call.authorizationOrThrow()
            val result = FriendsService.impureGenerate(
                context = context,
                authorization = authorization,
            )
            when (result) {
                is Unauthorized -> call.respondAuthorizationInvalid()
                is Success -> call.respond(result.toResponse())
            }
        }
    }

    @Suppress("ktlint:standard:max-line-length")
    private fun FriendsService.GenerateResult.Success.toResponse(): GenerateResponse =
        GenerateResponse(
            token = token.serializable(),
        )

    @Serializable
    private data class AddBody(
        val token: FriendTokenSerializable,
        val userId: UserIdSerializable,
    )

    @Serializable
    private sealed interface AddResponse {
        @Serializable
        @SerialName("FriendTokenExpired")
        data object FriendTokenExpired : AddResponse

        @Serializable
        @SerialName("Success")
        data object Success : AddResponse
    }

    fun impureAdd(context: AppContext) {
        context.routing.post("/friends/add") {
            val authorization = call.authorizationOrThrow()
            val body = call.receive<AddBody>()
            val result = FriendsService.impureAdd(
                context = context,
                authorization = authorization,
                token = body.token.typed(),
                userId = body.userId.typed(),
            )
            if (result is Unauthorized) {
                call.respondAuthorizationInvalid()
            } else {
                val response: AddResponse = when (result) {
                    is FriendTokenExpired -> FriendTokenExpired
                    is Success -> Success
                }
                call.respond(response)
            }
        }
    }
}
