package friendly.backend

import java.text.MessageFormat

object AuthMailService {
    fun send(
        context: AppContext,
        email: Email,
        localeCode: LocaleCode,
        loginCode: LoginCode,
    ) {
        val locale = AuthMailLocale.of(localeCode)
        val javaClass = AuthMailService::class.java
        val htmlTemplate = javaClass.getResource(locale.resourceName).readText()
        val htmlMessageFormat = MessageFormat(htmlTemplate)
        val stringified = "${loginCode.int / 10_000}-${loginCode.int % 10_000}"
        val args = arrayOf(stringified)
        val html = htmlMessageFormat.format(args)
        Smtp2goService.send(
            context = context,
            to = listOf(email),
            subject = locale.subject(stringified),
            html = EmailHtml(html),
        )
    }
}
