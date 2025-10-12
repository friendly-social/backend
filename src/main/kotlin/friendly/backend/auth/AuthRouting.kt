package friendly.backend.auth

import friendly.backend.AppContext
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.auth(context: AppContext) {
    route("auth") {
        generate(context)
    }
}
