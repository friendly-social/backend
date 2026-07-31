package friendly.backend

import kotlinx.serialization.Serializable

@Serializable
data class CommunityPostDescriptorSerializable(
    val id: CommunityPostIdSerializable,
    val accessHash: CommunityPostAccessHashSerializable,
) {
    fun typed(): CommunityPostDescriptor = CommunityPostDescriptor(
        id = id.typed(),
        accessHash = accessHash.typed(),
    )
}
