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
        val args = arrayOf(
            loginCode[0],
            loginCode[1],
            loginCode[2],
            loginCode[3],
            loginCode[4],
            loginCode[5],
            loginCode[6],
            loginCode[7],
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
