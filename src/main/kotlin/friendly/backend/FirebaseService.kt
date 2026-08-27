package friendly.backend

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FirebaseService")

object FirebaseService {
    suspend fun send(
        context: AppContext,
        firebaseToken: FirebaseToken,
        notification: NotificationDetails,
    ): Boolean {
        val messaging = context.firebase.messaging
        val serializable = notification.serializable()
        val string = Json.encodeToString(serializable)
        val message = Message.builder()
            .putData("details", string)
            .setToken(firebaseToken.string)
            .build()
        logger.info("Send: $notification")
        return try {
            withContext(Dispatchers.IO) {
                messaging.send(message)
                logger.info("Sent: $notification")
                true
            }
        } catch (exception: FirebaseMessagingException) {
            logger.info("Error: $notification")
            exception.printStackTrace()
            when (exception.messagingErrorCode) {
                UNREGISTERED -> runCatching {
                    unregister(context, firebaseToken)
                }.isSuccess
                INVALID_ARGUMENT -> {
                    logger.info("Invalid argument: $exception")
                    true
                }
                else -> false
            }
        }
    }

    suspend fun register(
        context: AppContext,
        authorization: Authorization,
        firebaseToken: FirebaseToken,
    ) = suspendTransaction(context.database) {
        TokensTable.updateFirebase(
            ownerId = authorization.id,
            token = authorization.token,
            firebaseToken = firebaseToken,
        )
    }

    suspend fun unregister(context: AppContext, firebaseToken: FirebaseToken) =
        suspendTransaction(context.database) {
            TokensTable.deleteFirebase(firebaseToken)
        }
}
