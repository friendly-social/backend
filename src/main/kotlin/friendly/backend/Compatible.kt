package friendly.backend

import io.ktor.server.routing.RoutingCall

@JvmInline
value class Compatible(val int: Int) {
    companion object {
        val Latest: Compatible = Compatible(2)
    }
}

fun RoutingCall.compatible(): Compatible {
    val int = request.headers["X-Compatible"]
        ?.toIntOrNull()
        ?: 1
    return Compatible(int)
}
