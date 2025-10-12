package friendly.backend.auth

import friendly.backend.AppContext
import friendly.backend.NicknameSerializable
import friendly.backend.TokenSerializable
import friendly.backend.UserIdSerializable
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

fun Route.generate(context: AppContext) {
    val usecase = GenerateAuthUsecase(context)
    generate(usecase)
}

@Serializable
data class GenerateAuthBody(val nickname: NicknameSerializable)

@Serializable
data class GenerateAuthResponse(
    val userId: UserIdSerializable,
    val token: TokenSerializable,
)

fun Route.generate(generateAuth: GenerateAuthUsecase) {
    post("/generate") {
        val body = call.receive<GenerateAuthBody>()
        val result = generateAuth(body.nickname.typed())
        call.respond(result.toResponse())
    }
}

private fun GenerateAuthUsecase.Result.toResponse(): GenerateAuthResponse =
    GenerateAuthResponse(userId.serializable(), token.serializable())
