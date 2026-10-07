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
            // todo: filter out decision not request after test
            //       then map entry to id
            val outgoing = FriendsTable
                .selectOutgoing(frontier)
                .filter { entry -> entry.toId !in visitedIds }
            val incoming = FriendsTable.select(
                fromIds = outgoing.map { entry -> entry.toId },
                toIds = frontier,
            ).associateBy { entry ->
                entry.descriptor
            }
            val mutual = outgoing.filter { outgoing ->
                val incomingDecision =
                    incoming[outgoing.descriptor.swap()]?.decision
                outgoing.decision == Request && incomingDecision == Request
            }
            for ((_, fromId, toId) in mutual) {
                require(toId !in visitedIds) {
                    "Should be filtered before, or there is a bug"
                }
                result += NetworkConnection(
                    degree = NetworkDegree(degree),
                    fromId = fromId,
                    toId = toId,
                )
            }
            frontier = mutual.map { (_, _, toId) -> toId }.distinct()
            visitedIds += frontier
        }
        result
    }
}
