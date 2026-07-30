package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Instant

object CommunityPostsTable : Table("community_posts") {
    private val idColumn = long("id").autoIncrement()
    private val ownerIdColumn = long("owner_id")
    private val textColumn = varchar("text", CommunityPostText.MaxLength)
    private val editedColumn = bool("edited").default(false)
    private val instantColumn = timestamp("instant")

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(
        ownerId: UserId,
        text: CommunityPostText,
        instant: Instant,
    ) {
        insert { statement ->
            statement[ownerIdColumn] = ownerId.long
            statement[textColumn] = text.string
            statement[instantColumn] = instant
        }
    }

    data class SelectResult(val entries: List<Entry>, val hasNext: Boolean)

    suspend fun select(
        ids: List<UserId>,
        before: CommunityPostId?,
        limit: Int,
    ): SelectResult {
        val rawIds = ids.map(UserId::long)
        val entries = selectAll()
            .where {
                (ownerIdColumn inList rawIds) and
                    (idColumn less (before?.long ?: Long.MAX_VALUE))
            }
            .orderBy(idColumn, DESC)
            .limit(limit + 1)
            .toList()
            .map { row ->
                Entry(
                    id = CommunityPostId(row[idColumn]),
                    ownerId = UserId(row[ownerIdColumn]),
                    text = CommunityPostText.orThrow(row[textColumn]),
                    instant = row[instantColumn],
                    edited = row[editedColumn],
                )
            }
        val hasNext = entries.size == limit + 1
        return SelectResult(
            entries = entries.dropLast(n = if (hasNext) 1 else 0),
            hasNext = hasNext,
        )
    }

    suspend fun delete(id: CommunityPostId): Boolean = deleteWhere {
        idColumn eq id.long
    } > 0

    suspend fun update(
        id: CommunityPostId,
        text: Field<CommunityPostText>?,
    ): Boolean {
        if (nothingChanged(text)) {
            return true
        }
        return update({ idColumn eq id.long }) { statement ->
            if (text != null) {
                statement[textColumn] = text.value.string
            }
            statement[editedColumn] = true
        } > 0
    }

    private fun nothingChanged(vararg fields: Field<*>?): Boolean =
        fields.all { field ->
            field == null
        }

    data class Entry(
        val id: CommunityPostId,
        val ownerId: UserId,
        val text: CommunityPostText,
        val instant: Instant,
        val edited: Boolean,
    )
}
