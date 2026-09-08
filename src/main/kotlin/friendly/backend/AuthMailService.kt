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
        val string = loginCode.int.toString()
        val dashed = "${string.take(4)}-${string.drop(4)}"
        val args = arrayOf(dashed)
        val html = htmlMessageFormat.format(args)
        Smtp2goService.send(
            context = context,
            to = listOf(email),
            subject = locale.subject(dashed),
            html = EmailHtml(html),
        )
    }
}
