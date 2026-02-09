package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

object LoginCodesTable : Table("login_codes") {
    private val emailColumn = varchar("email", Email.MaxLength)
    private val codeColumn = integer("code")
    private val expirationColumn = timestamp("expiration")
    private val attemptsColumn = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(emailColumn)

    suspend fun insert(
        email: Email,
        code: LoginCode,
        expiration: LoginCodeExpiration,
    ) {
        insert { statement ->
            statement[emailColumn] = email.string
            statement[codeColumn] = code.int
            statement[expirationColumn] = expiration.instant
        }
    }

    suspend fun select(email: Email): Entry? = selectAll()
        .where { (emailColumn eq email.string) }
        .map { row -> row.toEntry() }
        .firstOrNull()

    suspend fun updateAttempts(email: Email, attempts: LoginCodeAttempts) {
        update(
            where = { emailColumn eq email.string },
        ) { statement ->
            statement[attemptsColumn] = attempts.int
        }
    }

    suspend fun delete(email: Email) {
        deleteWhere { emailColumn eq email.string }
    }

    data class Entry(
        val email: Email,
        val code: LoginCode,
        val expiration: LoginCodeExpiration,
        val attempts: LoginCodeAttempts,
    )

    private fun ResultRow.toEntry(): Entry = Entry(
        email = Email.orThrow(this[emailColumn]),
        code = LoginCode.orThrow(this[codeColumn]),
        expiration = LoginCodeExpiration(this[expirationColumn]),
        attempts = LoginCodeAttempts.orThrow(this[attemptsColumn]),
    )
}
