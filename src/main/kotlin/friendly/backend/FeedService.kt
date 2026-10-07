package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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
                .associateBy { entry -> entry.toId }
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
            .filter { connection ->
                filterOutgoingWithForgettingCurve(context, connection, outgoing)
            }
            .groupBy { (degree) -> degree }
        val neighboringNetworkRaw =
            network.getOrElse(NetworkDegree.Two) { emptyList() }
        val extendedNetworkRaw =
            network.getOrElse(NetworkDegree.Three) { emptyList() } +
                network.getOrElse(NetworkDegree.Four) { emptyList() }
        val neighboringIds = neighboringNetworkRaw
            .flatMap { (_, fromId, toId) -> listOf(fromId, toId) }
        val extendedIds = extendedNetworkRaw.map { (_, _, toId) -> toId }
        val users = UsersService.details(
            context = context,
            fromId = authorization.id,
            ids = (neighboringIds + extendedIds).distinct(),
        )
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
            .sortedByDescending { entry -> entry.scoreMoreFilledFirst() }
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
            .sortedByDescending { entry -> entry.scoreMoreFilledFirst() }
        var entries = neighboringNetwork + extendedNetwork
        entries = entries.filter { entry ->
            incoming[entry.details.id] == Request
        } + entries.filter { entry ->
            entry.details.id !in incoming
        }
        val feed = FeedQueue(entries)
        return QueueResult.Success(feed)
    }

    /**
     * Filters out connections that were already answered with Request,
     * OR applies forgetting curve if decision was Decline.
     */
    private fun filterOutgoingWithForgettingCurve(
        context: AppContext,
        connection: NetworkConnection,
        outgoing: Map<UserId, FriendsTable.Entry>,
    ): Boolean {
        val entry = outgoing[connection.toId] ?: return true
        return when (entry.decision) {
            Request -> false
            Decline -> {
                val forgetIn = getExpectedForgetDuration(
                    declineTimes = entry.declineTimes,
                    declineForgetRandom = entry.declineForgetRandom,
                )
                entry.instant + forgetIn < context.clock.now()
            }
        }
    }

    fun getExpectedForgetDuration(
        declineTimes: Int,
        declineForgetRandom: Duration,
    ): Duration {
        require(declineTimes > 0) {
            "Can't get forget duration if wasn't declined"
        }
        val baseForget = when (declineTimes) {
            1 -> 30.days
            2 -> 90.days
            3 -> 360.days
            else -> 720.days
        }
        return baseForget + declineForgetRandom
    }

    /**
     * Sorting mask:
     *
     * X00_000 - whether user has avatar attached or not
     * 0XX_XXX - how many common friends there are
     * 0X0_000 - whether user has email attached or not
     * 0X0_000 - whether user has social link or not
     * 00X_XXX - how filled is user description, half of that if >= than 100 chars
     * 00X_XXX - how filled are interests, half of that if >= than 3 interests
     */
    private fun FeedQueue.Entry.scoreMoreFilledFirst(): Int {
        var score = 0

        if (details.avatar != null) {
            score += 100_000
        }

        if (details.email != null) {
            score += 10_000
        }

        if (details.socialLink != null) {
            score += 10_000
        }

        score += commonFriends.size * 10_000

        score += run {
            val string = details.description.string
            val maxScore = 1_000
            val firstHalf = if (string.length >= 100) maxScore / 2 else 0
            val secondHalf =
                1.0 * string.length / UserDescription.MaxLength * maxScore / 2
            (firstHalf + secondHalf).toInt()
        }

        score += run {
            val list = details.interests.raw
            val maxScore = 1_000
            val firstHalf = if (list.size >= 3) maxScore / 2 else 0
            val secondHalf =
                1.0 * list.size / InterestList.MaxSize * maxScore / 2
            (firstHalf + secondHalf).toInt()
        }

        return score
    }
}
