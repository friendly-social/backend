package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object FeedService {
    sealed interface QueueResult {
        data object Unauthorized : QueueResult
        data class Success(val details: FeedQueue) : QueueResult
    }

    suspend fun queue(
        context: AppContext,
        authorization: Authorization,
    ): QueueResult {
        AuthService
            .authorize(context, authorization)
            .onFailure { return QueueResult.Unauthorized }
        val outgoing = suspendTransaction(context.database) {
            FriendsTable
                .selectOutgoing(fromIds = listOf(authorization.id))
                .map { entry -> entry.toId }
                .toSet()
        }
        val incoming = suspendTransaction(context.database) {
            FriendsTable
                .selectIncoming(toIds = listOf(authorization.id))
                .associate { entry -> entry.fromId to entry.decision }
        }
        val network = NetworkService.networkConnections(
            context = context,
            fromId = authorization.id,
            maxDegrees = NetworkDegree.Four,
        )
            .filter { connection -> connection.toId !in outgoing }
            .groupBy { (degree) -> degree }
        val neighboringNetworkRaw =
            network.getOrElse(NetworkDegree.Two) { emptyList() }
        val extendedNetworkRaw =
            network.getOrElse(NetworkDegree.Three) { emptyList() } +
                network.getOrElse(NetworkDegree.Four) { emptyList() }
        val users = UsersService.details(
            context = context,
            fromId = authorization.id,
            ids = neighboringNetworkRaw.flatMap { (_, fromId, toId) ->
                listOf(fromId, toId)
            } + extendedNetworkRaw.map { (_, _, toId) -> toId },
        )
            .associateBy { user -> user!!.id }
        val neighboringNetwork = neighboringNetworkRaw
            .map { (_, fromId, toId) -> users[fromId]!! to users[toId]!! }
            .groupBy(
                keySelector = { (_, to) -> to },
                valueTransform = { (from) -> from },
            )
            .map { (details, commonFriends) ->
                FeedQueue.Entry(
                    isRequest = incoming[details.id] == Request,
                    isExtendedNetwork = false,
                    commonFriends = commonFriends,
                    details = details,
                )
            }
        val extendedNetwork = extendedNetworkRaw
            .map { (_, fromId, toId) -> fromId to users[toId]!! }
            .groupBy { (_, to) -> to }
            .map { (details, _) ->
                FeedQueue.Entry(
                    isRequest = incoming[details.id] == Request,
                    isExtendedNetwork = true,
                    commonFriends = emptyList(),
                    details = details,
                )
            }
        var entries = neighboringNetwork + extendedNetwork
        entries = entries.filter { entry ->
            incoming[entry.details.id] == Request
        } + entries.filter { entry ->
            entry.details.id !in incoming
        }
        val feed = FeedQueue(entries)
        return QueueResult.Success(feed)
    }
}
