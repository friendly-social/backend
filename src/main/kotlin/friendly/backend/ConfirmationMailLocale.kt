package friendly.backend

sealed interface ConfirmationMailLocale {
    val subject: EmailSubject
    val resourceName: String

    data object En : ConfirmationMailLocale {
        override val subject = EmailSubject("Friendly: Confirm email-address")
        override val resourceName = "/confirmation.email.en.html"
    }

    data object Ru : ConfirmationMailLocale {
        override val subject = EmailSubject("Friendly: Подтвердите email-адрес")
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
