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
        val token: TokenSerializable,
        val id: UserIdSerializable,
        val accessHash: UserAccessHashSerializable,
    )

    fun generateIn(context: AppContext) {
        context.routing.post("/auth/generate") {
            val body = call.receive<GenerateBody>()
            val result = TokensService.generateIn(
                context = context,
                nickname = body.nickname.typed(),
                description = body.description.typed(),
                interests = body.interests.typed(),
            )
            call.respond(result.toResponse())
        }
    }

    private fun TokensService.GenerateResult.toResponse(): GenerateResponse =
        GenerateResponse(
            token = token.serializable(),
            id = id.serializable(),
            accessHash = accessHash.serializable(),
        )
}
