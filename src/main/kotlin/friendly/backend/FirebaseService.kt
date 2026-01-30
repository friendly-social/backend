package friendly.backend

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object FirebaseService {
    suspend fun impureSend(
        context: AppContext,
        firebaseToken: FirebaseToken,
        notification: NotificationDetails,
    ): Boolean {
        val messaging = context.firebase.messaging
        val serializable = notification.serializable()
        val string = Json.encodeToString(serializable)
        val message = Message.builder()
            .putData("notification", string)
            .setToken(firebaseToken.string)
            .build()
        return try {
            messaging.send(message)
            true
        } catch (exception: FirebaseMessagingException) {
            when (exception.messagingErrorCode) {
                UNREGISTERED -> impureUnregisterToken(context, firebaseToken)
                else -> false
            }
        }
    }

    private suspend fun impureUnregisterToken(
        context: AppContext,
        firebaseToken: FirebaseToken,
    ): Boolean = try {
        suspendTransaction(context.database) {
            TokensTable.impureDeleteFirebase(firebaseToken)
        }
        true
    } catch (_: Throwable) {
        false
    }
}
