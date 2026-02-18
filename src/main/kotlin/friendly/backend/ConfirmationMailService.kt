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
        val args = arrayOf(
            confirmationCode[0],
            confirmationCode[1],
            confirmationCode[2],
            confirmationCode[3],
            confirmationCode[4],
            confirmationCode[5],
            confirmationCode[6],
            confirmationCode[7],
        )
        val html = htmlMessageFormat.format(args)
        Smtp2goService.send(
            context = context,
            to = listOf(email),
            subject = locale.subject,
            html = EmailHtml(html),
        )
    }
}
