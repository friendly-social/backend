package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

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
        val friendDetails = FriendsService
            .impureList(context, authorization.id)
        val networkDetails = NetworkDetails(friendDetails)
        return DetailsResult.Success(networkDetails)
    }

    suspend fun impureNetworkConnections(
        context: AppContext,
        fromId: UserId,
        maxDegrees: NetworkDegree,
    ): List<NetworkConnection> = suspendTransaction(context.database) {
        require(maxDegrees.int > 0)
        val visitedIds = mutableSetOf(fromId)
        val result = mutableListOf<NetworkConnection>()
        var frontier = listOf(fromId)
        for (degree in 1..maxDegrees.int) {
            if (frontier.isEmpty()) break
            val outgoing = FriendsTable.impureSelectOutgoing(frontier)
            val reversed = outgoing.map { outgoing ->
                outgoing.descriptor.swap()
            }
            val existing = FriendsTable.impureSelect(reversed).iterator()
            val mutual = outgoing.filter { outgoing ->
                val existing = existing.next()
                outgoing.decision == Request && existing == Request
            }
            for ((fromId, toId) in mutual) {
                if (toId in visitedIds) continue
                result += NetworkConnection(
                    degree = NetworkDegree(degree),
                    fromId,
                    toId,
                )
            }
            frontier = mutual.map { (_, toId) -> toId }
                .toSet()
                .filter { userId -> userId !in visitedIds }
            visitedIds += frontier
        }
        result
    }
}
