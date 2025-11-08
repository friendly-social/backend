package friendly.backend

object NetworkService {
    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data class Success(val details: NetworkDetails) : DetailsResult
    }

    suspend fun impureDetails(
        context: AppContext,
        authorization: Authorization,
    ): DetailsResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return DetailsResult.Unauthorized }
        val friendDetails = FriendsService.impureList(context, authorization.id)
        val networkDetails = NetworkDetails(friendDetails)
        return DetailsResult.Success(networkDetails)
    }
}
