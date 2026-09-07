package friendly.backend

sealed interface ConfirmationMailLocale {
    fun subject(code: String): EmailSubject
    val resourceName: String

    data object En : ConfirmationMailLocale {
        override fun subject(code: String) =
            EmailSubject("Friendly: Use $code to confirm email-address")
        override val resourceName = "/confirmation.email.en.html"
    }

    data object Ru : ConfirmationMailLocale {
        override fun subject(code: String) = EmailSubject(
            "Friendly: Введите $code и подтвердите email-адрес",
        )
        override val resourceName = "/confirmation.email.ru.html"
    }

    companion object {
        fun of(localeCode: LocaleCode): ConfirmationMailLocale =
            when (localeCode) {
                is LocaleCode.En -> En
                is LocaleCode.Ru -> Ru
            }
    }
}
