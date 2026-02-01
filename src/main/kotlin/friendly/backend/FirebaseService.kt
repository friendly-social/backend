package friendly.backend

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            .putData("details", string)
            .setToken(firebaseToken.string)
            .build()
        return try {
            withContext(Dispatchers.IO) {
                messaging.send(message)
                true
            }
        } catch (exception: FirebaseMessagingException) {
            when (exception.messagingErrorCode) {
                UNREGISTERED -> runCatching {
                    impureUnregister(context, firebaseToken)
                }.isSuccess
                else -> false
            }
        }
    }

    suspend fun impureRegister(
        context: AppContext,
        authorization: Authorization,
        firebaseToken: FirebaseToken,
    ) = suspendTransaction(context.database) {
        TokensTable.impureUpdateFirebase(
            ownerId = authorization.id,
            token = authorization.token,
            firebaseToken = firebaseToken,
        )
    }

    suspend fun impureUnregister(
        context: AppContext,
        firebaseToken: FirebaseToken,
    ) = suspendTransaction(context.database) {
        TokensTable.impureDeleteFirebase(firebaseToken)
    }
}
