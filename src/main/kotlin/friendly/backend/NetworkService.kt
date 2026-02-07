package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object NetworkService {
    sealed interface DetailsResult {
        data object Unauthorized : DetailsResult
        data class Success(val details: NetworkDetails) : DetailsResult
    }

    suspend fun details(
        context: AppContext,
        authorization: Authorization,
    ): DetailsResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return DetailsResult.Unauthorized }
        val friendDetails = FriendsService
            .list(context, authorization.id)
        val networkDetails = NetworkDetails(friendDetails)
        return DetailsResult.Success(networkDetails)
    }

    suspend fun networkConnections(
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
            val outgoing = FriendsTable.selectOutgoing(frontier)
            val reversed = outgoing.map { outgoing ->
                outgoing.descriptor.swap()
            }
            val existing = FriendsTable.select(reversed).iterator()
            val mutual = outgoing.filter { outgoing ->
                val existing = existing.next()
                outgoing.decision == Request && existing == Request
            }
            for ((fromId, toId) in mutual) {
                if (toId in visitedIds) continue
                result += NetworkConnection(
                    degree = NetworkDegree(degree),
                    fromId = fromId,
                    toId = toId,
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
