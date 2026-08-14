package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
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
    private val accessHashColumn =
        varchar("access_hash", CommunityPostAccessHash.Length)
    private val ownerIdColumn = long("owner_id")
    private val textColumn = varchar("text", CommunityPostText.MaxLength)
    private val editedColumn = bool("edited").default(false)
    private val instantColumn = timestamp("instant")
    private val replyToColumn = long("reply_to").nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(
        accessHash: CommunityPostAccessHash,
        ownerId: UserId,
        text: CommunityPostText,
        instant: Instant,
        replyTo: CommunityPostId?,
    ): CommunityPostId {
        val statement = insert { statement ->
            statement[accessHashColumn] = accessHash.string
            statement[ownerIdColumn] = ownerId.long
            statement[textColumn] = text.string
            statement[instantColumn] = instant
            statement[replyToColumn] = replyTo?.long
        }
        return CommunityPostId(statement[idColumn])
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
                    (idColumn less (before?.long ?: Long.MAX_VALUE)) and
                    (replyToColumn eq null)
            }
            .orderBy(idColumn, DESC)
            .limit(limit + 1)
            .toList()
            .map { row -> row.toEntry() }
        return if (entries.size == limit + 1) {
            SelectResult(
                entries = entries.dropLast(1),
                hasNext = true,
            )
        } else {
            SelectResult(
                entries = entries,
                hasNext = false,
            )
        }
    }

    suspend fun selectReplies(
        replyTo: CommunityPostId,
        after: CommunityPostId?,
        limit: Int,
    ): SelectResult {
        val entries = selectAll()
            .where {
                (replyToColumn eq replyTo.long) and
                    (idColumn greater (after?.long ?: MIN_VALUE))
            }
            .orderBy(idColumn, ASC)
            .limit(limit + 1)
            .toList()
            .map { row -> row.toEntry() }
        return if (entries.size == limit + 1) {
            SelectResult(
                entries = entries.dropLast(1),
                hasNext = true,
            )
        } else {
            SelectResult(
                entries = entries,
                hasNext = false,
            )
        }
    }

    suspend fun selectById(ids: List<CommunityPostId>): List<Entry> {
        val raw = ids.map { id -> id.long }
        val map = selectAll()
            .where { idColumn inList raw }
            .map { row -> row.toEntry() }
            .toList()
            .associateBy { entry -> entry.id }
        return ids.map { id -> map.getValue(id) }
    }

    suspend fun selectByDescriptor(
        descriptors: List<CommunityPostDescriptor>,
    ): List<Entry?> {
        val raw = descriptors.map { (id, accessHash) ->
            id.long to accessHash.string
        }
        val map = selectAll()
            .where { (idColumn to accessHashColumn) inList raw }
            .map { row -> row.toEntry() }
            .toList()
            .associateBy { entry -> entry.id }
        return descriptors.map { (id) -> map.get(id) }
    }

    suspend fun exists(descriptor: CommunityPostDescriptor): Boolean =
        selectAll()
            .where {
                (idColumn eq descriptor.id.long) and
                    (accessHashColumn eq descriptor.accessHash.string)
            }
            .firstOrNull() != null

    suspend fun delete(id: CommunityPostId, ownerId: UserId): Boolean =
        deleteWhere {
            (idColumn eq id.long) and (ownerIdColumn eq ownerId.long)
        } > 0

    suspend fun update(
        id: CommunityPostId,
        ownerId: UserId,
        text: Field<CommunityPostText>?,
    ): Boolean {
        if (nothingChanged(text)) {
            return true
        }
        return update({
            (idColumn eq id.long) and (ownerIdColumn eq ownerId.long)
        }) { statement ->
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
        val accessHash: CommunityPostAccessHash,
        val ownerId: UserId,
        val text: CommunityPostText,
        val instant: Instant,
        val edited: Boolean,
    )

    private fun ResultRow.toEntry(): Entry = Entry(
        id = CommunityPostId(this[idColumn]),
        accessHash = CommunityPostAccessHash.orThrow(
            this[accessHashColumn],
        ),
        ownerId = UserId(this[ownerIdColumn]),
        text = CommunityPostText.orThrow(this[textColumn]),
        instant = this[instantColumn],
        edited = this[editedColumn],
    )
}
