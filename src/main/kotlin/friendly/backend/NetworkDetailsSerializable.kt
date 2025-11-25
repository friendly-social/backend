package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class NetworkDetailsSerializable(
    val friends: List<UserDetailsSerializable>,
)
