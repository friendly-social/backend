package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

object AuthRouting {

    @Serializable
    private data class GenerateBody(
        val nickname: NicknameSerializable,
        val description: UserDescriptionSerializable,
        val interests: InterestListSerializable,
        val avatar: FileDescriptorSerializable?,
        val socialLink: SocialLinkSerializable?,
    )

    @Serializable
    private data class GenerateResponse(
        val token: TokenSerializable,
        val id: UserIdSerializable,
        val accessHash: UserAccessHashSerializable,
    )

    fun generate(context: AppContext) {
        context.routing.post("/auth/generate") {
            val body = call.receive<GenerateBody>()
            val result = TokensService.generate(
                context = context,
                nickname = body.nickname.typed(),
                description = body.description.typed(),
                interests = body.interests.typed(),
                avatar = body.avatar?.typed(),
                socialLink = body.socialLink?.typed(),
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

    @Serializable
    private data class FirebaseBody(
        val firebaseToken: FirebaseTokenSerializable,
    )

    @Serializable
    data object FirebaseResponse

    fun firebase(context: AppContext) {
        context.routing.post("/auth/firebase") {
            val authorization = call.authorization()
            val body = call.receive<FirebaseBody>()
            TokensService.firebase(
                context = context,
                authorization = authorization,
                firebaseToken = body.firebaseToken.typed(),
            )
            call.respond(FirebaseResponse)
        }
    }

    @Serializable
    private data class EmailBody(val email: EmailSerializable)

    fun email(context: AppContext) {
        context.routing.post("/auth/email") {
            val localeCode = call.localeCode()
            val body = call.receive<EmailBody>()
            val email = body.email.typed()
            val result = AuthService.email(context, email, localeCode)
            when (result) {
                UnknownEmail -> call.respond(HttpStatusCode.Unauthorized)
                Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }

    @Serializable
    private data class LoginBody(
        val email: EmailSerializable,
        val code: LoginCodeSerializable,
    )

    @Serializable
    private data class LoginResponse(
        val token: TokenSerializable,
        val id: UserIdSerializable,
        val accessHash: UserAccessHashSerializable,
    )

    fun login(context: AppContext) {
        context.routing.post("/auth/login") {
            val body = call.receive<LoginBody>()
            val result = AuthService.login(
                context = context,
                email = body.email.typed(),
                code = body.code.typed(),
            )
            when (result) {
                InvalidCode -> call.respond(HttpStatusCode.Forbidden)
                is Success -> {
                    val response = result.toResponse()
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }

    private fun AuthService.LoginResult.Success.toResponse(): LoginResponse =
        LoginResponse(
            token = token.serializable(),
            id = id.serializable(),
            accessHash = accessHash.serializable(),
        )

    @Serializable
    data object LogoutResponse

    fun logout(context: AppContext) {
        context.routing.post("/auth/logout") {
            val authorization = call.authorization()
            TokensService.logout(
                context = context,
                authorization = authorization,
            )
            call.respond(LogoutResponse)
        }
    }
}
