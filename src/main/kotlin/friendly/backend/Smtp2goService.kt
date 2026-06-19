package friendly.backend

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Smtp2goService {
    private val baseUrl = "https://api.smtp2go.com/v3"

    /**
     * This is a blackbox method as of now. It does not guarantee that anything
     * is going to be sent. It will try to use smtp2go to deliver the email,
     * but if something goes wrong, it will not report a failure. And that is a
     * deliberate design choice for now, since it's easier to implement.
     */
    fun send(
        context: AppContext,
        to: List<Email>,
        subject: EmailSubject,
        html: EmailHtml,
    ) {
        require(to.isNotEmpty())
        context.smtp2go.gracefulScope.launch {
            val httpClient = context.smtp2go.httpClient
            val request = Request(
                sender = "Friendly <noreply@getfriend.ly>",
                to = to.map { email -> email.string },
                subject = subject.string,
                htmlBody = html.string,
            )
            try {
                val token = context.smtp2go.token.string
                httpClient.post("$baseUrl/email/send") {
                    headers["X-Smtp2go-Api-Key"] = token
                    setBody(request)
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
            }
        }
    }

    @Serializable
    data class Request(
        @SerialName("sender")
        val sender: String,
        @SerialName("to")
        val to: List<String>,
        @SerialName("subject")
        val subject: String,
        @SerialName("html_body")
        val htmlBody: String,
    )
}
