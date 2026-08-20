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
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Instant

object CommunityPostsTable : Table("community_posts") {
    private val typeColumn = enumeration<Type>("type").default(Type.Plain)

    private val idColumn = long("id").autoIncrement()
    private val accessHashColumn =
        varchar("access_hash", CommunityPostAccessHash.Length)
    private val instantColumn = timestamp("instant")
    private val replyToColumn = long("reply_to").nullable()

    private val plainOwnerIdColumn = long("plain_owner_id").nullable()
    private val plainTextColumn = varchar(
        "plain_text",
        CommunityPostText.MaxLength,
    ).nullable()
    private val plainEditedColumn = bool("plain_edited")
        .default(false).nullable()

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
            statement[instantColumn] = instant
            statement[plainOwnerIdColumn] = ownerId.long
            statement[plainTextColumn] = text.string
            statement[replyToColumn] = replyTo?.long
        }
        return CommunityPostId(statement[idColumn])
    }

    data class SelectResult(val entries: List<Entry>, val hasNext: Boolean)

    suspend fun selectFrom(
        ids: List<UserId>,
        before: CommunityPostId?,
        limit: Int,
        withDeleted: Boolean,
    ): SelectResult {
        val rawIds = ids.map(UserId::long)
        val entries = selectAll(withDeleted)
            .andWhere {
                (plainOwnerIdColumn inList rawIds) and
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
        withDeleted: Boolean,
    ): SelectResult {
        val entries = selectAll(withDeleted)
            .andWhere {
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

    suspend fun selectById(
        ids: List<CommunityPostId>,
        withDeleted: Boolean,
    ): List<Entry> {
        val raw = ids.map { id -> id.long }
        val map = selectAll(withDeleted)
            .andWhere { idColumn inList raw }
            .map { row -> row.toEntry() }
            .toList()
            .associateBy { entry -> entry.id }
        return ids.map { id -> map.getValue(id) }
    }

    suspend fun selectByDescriptor(
        descriptors: List<CommunityPostDescriptor>,
        withDeleted: Boolean,
    ): List<Entry?> {
        val raw = descriptors.map { (id, accessHash) ->
            id.long to accessHash.string
        }
        val map = selectAll(withDeleted)
            .andWhere { (idColumn to accessHashColumn) inList raw }
            .map { row -> row.toEntry() }
            .toList()
            .associateBy { entry -> entry.id }
        return descriptors.map { (id) -> map.get(id) }
    }

    suspend fun exists(
        descriptor: CommunityPostDescriptor,
        withDeleted: Boolean,
    ): Boolean = selectAll(withDeleted)
        .andWhere {
            (idColumn eq descriptor.id.long) and
                (accessHashColumn eq descriptor.accessHash.string)
        }
        .firstOrNull() != null

    suspend fun delete(id: CommunityPostId, ownerId: UserId): Boolean = update(
        { (idColumn eq id.long) and (plainOwnerIdColumn eq ownerId.long) },
    ) { statement ->
        statement[typeColumn] = Type.Deleted
        statement[plainOwnerIdColumn] = null
        statement[plainTextColumn] = null
        statement[plainEditedColumn] = null
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
            (idColumn eq id.long) and (plainOwnerIdColumn eq ownerId.long)
        }) { statement ->
            if (text != null) {
                statement[plainTextColumn] = text.value.string
            }
            statement[plainEditedColumn] = true
        } > 0
    }

    private fun nothingChanged(vararg fields: Field<*>?): Boolean =
        fields.all { field ->
            field == null
        }

    private fun selectAll(withDeleted: Boolean): Query =
        if (withDeleted == false) {
            selectAll().where { typeColumn neq Type.Deleted }
        } else {
            selectAll()
        }

    sealed interface Entry {
        val id: CommunityPostId
        val accessHash: CommunityPostAccessHash
        val instant: Instant

        val ownerId: UserId?

        data class Plain(
            override val id: CommunityPostId,
            override val accessHash: CommunityPostAccessHash,
            override val instant: Instant,
            override val ownerId: UserId?,
            val text: CommunityPostText,
            val edited: Boolean,
        ) : Entry

        data class Deleted(
            override val id: CommunityPostId,
            override val accessHash: CommunityPostAccessHash,
            override val instant: Instant,
        ) : Entry {
            override val ownerId: Nothing? get() = null
        }
    }

    private fun ResultRow.toEntry(): Entry {
        val type = this[typeColumn]
        val id = CommunityPostId(this[idColumn])
        val accessHash = CommunityPostAccessHash.orThrow(this[accessHashColumn])
        val instant = this[instantColumn]
        return when (type) {
            Plain -> Entry.Plain(
                id,
                accessHash,
                instant,
                ownerId = UserId(this[plainOwnerIdColumn]!!),
                text = CommunityPostText.orThrow(this[plainTextColumn]!!),
                edited = this[plainEditedColumn]!!,
            )
            Deleted -> Entry.Deleted(id, accessHash, instant)
        }
    }

    enum class Type {
        Plain,
        Deleted,
    }
}
