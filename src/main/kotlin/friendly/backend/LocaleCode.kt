package friendly.backend

import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.SerializationException

sealed interface LocaleCode {
    data object En : LocaleCode
    data object Ru : LocaleCode
}

val provideLocaleMessage = """
Locale is required for this endpoint. Provide 'X-Locale' header with either 'en' or 'ru' value.
"""

fun RoutingCall.localeCode(): LocaleCode {
    val string = request.headers["X-Locale"]
        ?: throw SerializationException(provideLocaleMessage)
    return when (string) {
        "en" -> En
        "ru" -> Ru
        else -> throw SerializationException(provideLocaleMessage)
    }
}
