package friendly.backend

import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object AlertsService {
    suspend fun post(context: AppContext, payload: AlertPayload) {
        suspendTransaction(context.database) {
            AlertsTable.insert(payload)
        }
    }
}
