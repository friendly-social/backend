package friendly.backend.communitypostentity

sealed interface CommunityPostEntityDetails {
    fun serializable(): CommunityPostEntitySerializable
}
