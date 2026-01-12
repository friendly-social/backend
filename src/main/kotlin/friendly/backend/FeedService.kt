package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object FeedService {
    sealed interface QueueResult {
        data object Unauthorized : QueueResult
        data class Success(val details: FeedQueue) : QueueResult
    }

    suspend fun impureQueue(
        context: AppContext,
        authorization: Authorization,
    ): QueueResult {
        AuthService
            .impureAuthorize(context, authorization)
            .onFailure { return QueueResult.Unauthorized }
        val outgoing = suspendTransaction(context.database) {
            FriendsTable
                .impureSelectOutgoing(fromIds = listOf(authorization.id))
                .map { entry -> entry.toId }
        }
        val network = NetworkService.impureNetworkConnections(
            context = context,
            fromId = authorization.id,
            maxDegrees = NetworkDegree.Three,
        )
            .filter { connection -> connection.toId !in outgoing }
            .groupBy { (degree) -> degree }
        val secondDegreeRaw = network
            .getOrElse(NetworkDegree.Two) { emptyList() }
        val thirdDegreeRaw = network
            .getOrElse(NetworkDegree.Three) { emptyList() }
        val users = UsersService.impureDetails(
            context = context,
            ids = secondDegreeRaw.flatMap { (_, fromId, toId) ->
                listOf(fromId, toId)
            } + thirdDegreeRaw.flatMap { (_, fromId, toId) ->
                listOf(fromId, toId)
            },
        )
            .map { user -> user ?: error("All users should be found") }
            .iterator()
        val secondDegree = secondDegreeRaw
            .map { users.next() to users.next() } // from, to
            .groupBy(
                keySelector = { (_, to) -> to },
                valueTransform = { (from) -> from },
            )
            .map { (details, commonFriends) ->
                FeedQueue.Entry(
                    isExtendedNetwork = false,
                    commonFriends = commonFriends,
                    details = details,
                )
            }
        val thirdDegree = thirdDegreeRaw
            .map { users.next() to users.next() } // from, to
            .groupBy { (_, to) -> to }
            .map { (details, _) ->
                FeedQueue.Entry(
                    isExtendedNetwork = true,
                    commonFriends = emptyList(),
                    details = details,
                )
            }
        val feed = FeedQueue(entries = secondDegree + thirdDegree)
        return QueueResult.Success(feed)
    }
}
