package friendly.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

object UsersRouting {
    fun details(context: AppContext) {
        context.routing.get("/users/details/{id?}/{accessHash?}") {
            val authorization = call.authorization()
            val descriptor = call.descriptorOrThrow()
            val result = UsersService.details(
                context = context,
                authorization = authorization,
                descriptor = descriptor,
            )
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is NotFound -> call.respond(HttpStatusCode.NotFound)
                is Success -> call.respond(result.details.serializable())
            }
        }
    }

    @Serializable
    data class EditBody(
        val nickname: FieldSerializable<NicknameSerializable>? = null,
        val description: FieldSerializable<UserDescriptionSerializable>? = null,
        val interests: FieldSerializable<InterestListSerializable>? = null,
        val avatar: FieldSerializable<FileDescriptorSerializable?>? = null,
        val socialLink: FieldSerializable<SocialLinkSerializable?>? = null,
    )

    fun edit(context: AppContext) {
        context.routing.patch("/users/edit") {
            val authorization = call.authorization()
            val body = call.receive<EditBody>()
            val result = with(body) {
                UsersService.edit(
                    context = context,
                    authorization = authorization,
                    nickname = nickname?.typed { value -> value.typed() },
                    description = description?.typed { value -> value.typed() },
                    interests = interests?.typed { value -> value.typed() },
                    avatar = avatar?.typed { value -> value?.typed() },
                    socialLink = socialLink?.typed { value -> value?.typed() },
                )
            }
            when (result) {
                is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                is Success -> call.respond(HttpStatusCode.OK)
            }
        }
    }
}

private fun RoutingCall.descriptorOrThrow(): UsersService.DetailsDescriptor {
    val id = parameters["id"]
        ?.toLong()
        ?.let(::UserIdSerializable)
        ?.typed()
    val accessHash = parameters["accessHash"]
        ?.let(::UserAccessHashSerializable)
        ?.typed()
    return when {
        id == null && accessHash == null ->
            UsersService.DetailsDescriptor.Self
        id != null && accessHash != null ->
            UsersService.DetailsDescriptor.Other(id, accessHash)
        else -> throw SerializationException(
            "Either omit 'id' and 'accessHash' or include both of them",
        )
    }
}
