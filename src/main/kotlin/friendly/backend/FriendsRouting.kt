package friendly.backend

import io.ktor.server.response.respond
import io.ktor.server.routing.post
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
                is Unauthorized -> call.respondBadRequest(
                    message = "Provided Authorization is invalid"
                )
                is Success -> call.respond(result.toResponse())
            }
        }
    }

    @Suppress("ktlint:standard:max-line-length")
    private fun FriendsService.GenerateResult.Success.toResponse(): GenerateResponse =
        GenerateResponse(
            token = token.serializable(),
        )
}
