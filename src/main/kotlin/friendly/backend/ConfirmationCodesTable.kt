package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert

object ConfirmationCodesTable : Table("confirmation_codes") {
    private val ownerIdColumn = long("owner_id")
    private val emailColumn = varchar("email", Email.MaxLength)
    private val codeColumn = integer("code")
    private val expirationColumn = timestamp("expiration")
    private val attemptsColumn = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(emailColumn)

    suspend fun upsert(
        ownerId: UserId,
        email: Email,
        code: ConfirmationCode,
        expiration: ConfirmationCodeExpiration,
    ) {
        upsert { statement ->
            statement[ownerIdColumn] = ownerId.long
            statement[emailColumn] = email.string
            statement[codeColumn] = code.int
            statement[expirationColumn] = expiration.instant
        }
    }

    suspend fun select(email: Email): Entry? = selectAll()
        .where { (emailColumn eq email.string) }
        .map { row -> row.toEntry() }
        .firstOrNull()

    suspend fun select(ownerId: UserId): Entry? = selectAll()
        .where { (ownerIdColumn eq ownerId.long) }
        .map { row -> row.toEntry() }
        .firstOrNull()

    suspend fun updateAttempts(
        ownerId: UserId,
        attempts: ConfirmationCodeAttempts,
    ) {
        update(
            where = { ownerIdColumn eq ownerId.long },
        ) { statement ->
            statement[attemptsColumn] = attempts.int
        }
    }

    suspend fun delete(ownerId: UserId) {
        deleteWhere { ownerIdColumn eq ownerId.long }
    }

    data class Entry(
        val ownerId: UserId,
        val email: Email,
        val code: ConfirmationCode,
        val expiration: ConfirmationCodeExpiration,
        val attempts: ConfirmationCodeAttempts,
    )

    private fun ResultRow.toEntry(): Entry = Entry(
        ownerId = UserId(this[ownerIdColumn]),
        email = Email.orThrow(this[emailColumn]),
        code = ConfirmationCode.orThrow(this[codeColumn]),
        expiration = ConfirmationCodeExpiration(this[expirationColumn]),
        attempts = ConfirmationCodeAttempts.orThrow(this[attemptsColumn]),
    )
}
