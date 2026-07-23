package friendly.backend

import io.ktor.server.routing.RoutingCall

data class CursorId(val string: String) {
    fun serializable(): CursorIdSerializable = CursorIdSerializable(string)
}

fun RoutingCall.cursorIdOrNull(name: String): CursorId? =
    parameters[name]?.let(::CursorId)
