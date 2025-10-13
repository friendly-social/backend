package friendly.backend

import friendly.backend.AppContext
import friendly.backend.NicknameSerializable
import friendly.backend.TokenSerializable
import friendly.backend.UserIdSerializable
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class AuthGenerateBody(
    val nickname: NicknameSerializable,
    val description: UserDescriptionSerializable,
    val interests: List<InterestSerializable>,
)

@Serializable
data class AuthGenerateResponse(
    val userId: UserIdSerializable,
    val token: TokenSerializable,
)

fun Route.authGenerate(context: AppContext) {
    post("/auth/generate") {
        val body = call.receive<AuthGenerateBody>()
        val result = generateToken(
            context = context,
            nickname = body.nickname.typed(),
            description = body.description.typed(),
            interests = body.interests.typed(),
        )
        call.respond(result.toResponse())
    }
}

fun GenerateTokenResult.toResponse(): AuthGenerateResponse =
    AuthGenerateResponse(userId.serializable(), token.serializable())
