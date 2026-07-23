package friendly.backend

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.time.Instant

object CommunityPostsTable : Table("community_posts") {
    private val idColumn = long("id").autoIncrement()
    private val ownerIdColumn = long("owner_id")
    private val textColumn = varchar("text", CommunityPostText.MaxLength)
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

    suspend fun select(
        ids: List<UserId>,
        before: CommunityPostId?,
    ): List<Entry> {
        val rawIds = ids.map(UserId::long)
        return selectAll()
            .where {
                (ownerIdColumn inList rawIds) and
                    (idColumn less (before?.long ?: Long.MAX_VALUE))
            }
            .orderBy(idColumn, DESC)
            .limit(1024)
            .toList()
            .map { row ->
                Entry(
                    id = CommunityPostId(row[idColumn]),
                    ownerId = UserId(row[ownerIdColumn]),
                    text = CommunityPostText.orThrow(row[textColumn]),
                    instant = row[instantColumn],
                )
            }
    }

    data class Entry(
        val id: CommunityPostId,
        val ownerId: UserId,
        val text: CommunityPostText,
        val instant: Instant,
    )
}
