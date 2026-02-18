package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

object EmailRouting {
    @Serializable
    private data class LinkBody(val email: EmailSerializable)

    fun link(context: AppContext) {
        context.routing.post("/email/link") {
            val authorization = call.authorization()
            val localeCode = call.localeCode()
            val body = call.receive<LinkBody>()
            val email = body.email.typed()
            val result = EmailService.link(
                context = context,
                authorization = authorization,
                email = email,
                localeCode = localeCode,
            )
            when (result) {
                Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                EmailAlreadyUsed -> call.respond(HttpStatusCode.Conflict)
                Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }

    @Serializable
    data class ConfirmBody(val code: ConfirmationCodeSerializable)

    fun confirm(context: AppContext) {
        context.routing.post("/email/confirm") {
            val authorization = call.authorization()
            val body = call.receive<ConfirmBody>()
            val code = body.code.typed()
            val result = EmailService.confirm(context, authorization, code)
            when (result) {
                Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                InvalidCode -> call.respond(HttpStatusCode.Forbidden)
                Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }

    fun unlink(context: AppContext) {
        context.routing.post("/email/unlink") {
            val authorization = call.authorization()
            val result = EmailService.unlink(context, authorization)
            when (result) {
                Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }
}
