package friendly.backend

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class FirebaseTokenSerializable(val string: String) {
    init {
        require(string.length <= FirebaseToken.MaxLength) {
            "Firebase token is too large to be stored."
        }
    }

    fun typed(): FirebaseToken = FirebaseToken.orThrow(string = string)
}
