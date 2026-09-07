package friendly.backend

import java.text.MessageFormat

object ConfirmationMailService {
    fun send(
        context: AppContext,
        email: Email,
        localeCode: LocaleCode,
        confirmationCode: ConfirmationCode,
    ) {
        val locale = ConfirmationMailLocale.of(localeCode)
        val javaClass = ConfirmationMailService::class.java
        val htmlTemplate = javaClass.getResource(locale.resourceName).readText()
        val htmlMessageFormat = MessageFormat(htmlTemplate)
        val stringified =
            "${confirmationCode.int / 10_000}-${confirmationCode.int % 10_000}"
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
