package friendly.backend

sealed interface AuthMailLocale {
    val subject: EmailSubject
    val resourceName: String

    data object En : AuthMailLocale {
        override val subject =
            EmailSubject("Friendly: Login Code")
        override val resourceName = "/auth.email.en.html"
    }

    data object Ru : AuthMailLocale {
        override val subject =
            EmailSubject("Friendly: код для входа в приложение")
        override val resourceName = "/auth.email.ru.html"
    }

    companion object {
        fun of(localeCode: LocaleCode): AuthMailLocale = when (localeCode) {
            is LocaleCode.En -> En
            is LocaleCode.Ru -> Ru
        }
    }
}
