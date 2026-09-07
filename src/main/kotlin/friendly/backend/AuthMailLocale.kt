package friendly.backend

sealed interface AuthMailLocale {
    fun subject(code: String): EmailSubject
    val resourceName: String

    data object En : AuthMailLocale {
        override fun subject(code: String) =
            EmailSubject("Friendly: Use $code to login")
        override val resourceName = "/auth.email.en.html"
    }

    data object Ru : AuthMailLocale {
        override fun subject(code: String) =
            EmailSubject("Friendly: Введите $code для входа в приложение")
        override val resourceName = "/auth.email.ru.html"
    }

    companion object {
        fun of(localeCode: LocaleCode): AuthMailLocale = when (localeCode) {
            is LocaleCode.En -> En
            is LocaleCode.Ru -> Ru
        }
    }
}
