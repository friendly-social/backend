package friendly.backend

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

object AuthRouting {

    @Serializable
    private data class GenerateBody(
        val nickname: NicknameSerializable,
        val description: UserDescriptionSerializable,
        val interests: List<InterestSerializable>,
    )

    @Serializable
    private data class GenerateResponse(
        val userId: UserIdSerializable,
        val token: TokenSerializable,
    )

    fun generateIn(context: AppContext) {
        context.routing.post("/auth/generate") {
            val body = call.receive<GenerateBody>()
            val result = TokenService.generateIn(
                context = context,
                nickname = body.nickname.typed(),
                description = body.description.typed(),
                interests = body.interests.typed(),
            )
            call.respond(result.toResponse())
        }
    }

    private fun TokenService.GenerateResult.toResponse(): GenerateResponse =
        GenerateResponse(userId.serializable(), token.serializable())
}
