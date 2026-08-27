package friendly.backend

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FirebaseService")

object FirebaseService {
    suspend fun send(
        context: AppContext,
        firebaseToken: FirebaseToken,
        id: NotificationId,
    ): Boolean {
        val messaging = context.firebase.messaging
        val message = Message.builder()
            .putData("id", "${id.long}")
            .setToken(firebaseToken.string)
            .build()
        logger.info("Send: $id")
        return try {
            withContext(Dispatchers.IO) {
                messaging.send(message)
                logger.info("Sent: $id")
                true
            }
        } catch (exception: FirebaseMessagingException) {
            logger.info("Error: $id")
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
