package friendly.backend

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
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
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
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

    suspend fun selectReplierIds(
        ids: List<CommunityPostId>,
    ): List<List<UserId>> {
        val rawIds = ids.map { id -> id.long }
        val sql = """
        WITH replies AS (
                SELECT
                        community_posts_path.reply_to,
                        community_posts.plain_owner_id,
                        MAX(community_posts.instant) as instant
                FROM community_posts_path JOIN community_posts
                ON community_posts_path.post_id = community_posts.id
                WHERE
                        community_posts_path.reply_to = ANY(?) AND
                        community_posts.plain_owner_id IS NOT NULL
                GROUP BY community_posts_path.reply_to, community_posts.plain_owner_id
        ),
        most_recent AS (
                SELECT reply_to, plain_owner_id,
                        ROW_NUMBER() OVER (
                                PARTITION BY reply_to
                                ORDER BY instant DESC
                        ) as rank
                FROM replies
        )
        SELECT reply_to, plain_owner_id from most_recent WHERE rank <= 10;
        """.trimIndent()
        val arg = ArrayColumnType<_, List<Any?>>(LongColumnType()) to rawIds
        val results = TransactionManager.current().exec(
            stmt = sql,
            args = listOf(arg),
        ) { row ->
            val replyTo = row.get("reply_to", Long::class.java)!!
            val plainOwnerId = row.get("plain_owner_id", Long::class.java)!!
            CommunityPostId(replyTo) to UserId(plainOwnerId)
        } ?: emptyFlow()
        val map = results
            .toList()
            .filterNotNull()
            .groupBy(
                keySelector = { (replyTo) -> replyTo },
                valueTransform = { (_, plainOwnerId) -> plainOwnerId },
            )
        return ids.map { id -> map[id] ?: emptyList<UserId>() }
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
    ): List<Entry?> {
        val raw = ids.map { id -> id.long }
        val map = selectAll(withDeleted)
            .andWhere { idColumn inList raw }
            .map { row -> row.toEntry() }
            .toList()
            .associateBy { entry -> entry.id }
        return ids.map { id -> map[id] }
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

    suspend fun delete(id: CommunityPostId) {
        deleteWhere { idColumn eq id.long }
    }

    suspend fun updateToDeleted(id: CommunityPostId): Boolean = update(
        { idColumn eq id.long },
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
        val replyTo: CommunityPostId?

        val ownerId: UserId?

        data class Plain(
            override val id: CommunityPostId,
            override val accessHash: CommunityPostAccessHash,
            override val instant: Instant,
            override val ownerId: UserId,
            override val replyTo: CommunityPostId?,
            val text: CommunityPostText,
            val edited: Boolean,
        ) : Entry

        data class Deleted(
            override val id: CommunityPostId,
            override val accessHash: CommunityPostAccessHash,
            override val instant: Instant,
            override val replyTo: CommunityPostId?,
        ) : Entry {
            override val ownerId: Nothing? get() = null
        }
    }

    private fun ResultRow.toEntry(): Entry {
        val type = this[typeColumn]
        val id = CommunityPostId(this[idColumn])
        val accessHash = CommunityPostAccessHash.orThrow(this[accessHashColumn])
        val instant = this[instantColumn]
        val replyTo = this[replyToColumn]?.let(::CommunityPostId)
        return when (type) {
            Plain -> Entry.Plain(
                id = id,
                accessHash = accessHash,
                instant = instant,
                replyTo = replyTo,
                ownerId = UserId(this[plainOwnerIdColumn]!!),
                text = CommunityPostText.orThrow(this[plainTextColumn]!!),
                edited = this[plainEditedColumn]!!,
            )
            Deleted -> Entry.Deleted(
                id = id,
                accessHash = accessHash,
                instant = instant,
                replyTo = replyTo,
            )
        }
    }

    enum class Type {
        Plain,
        Deleted,
    }
}
