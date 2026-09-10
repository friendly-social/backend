package friendly.backend.communitypostentity

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object CommunityPostEntityMentionsTable : Table("community_post_entity_mentions") {
    private val idColumn = long("id")
    private val targetColumn = long("target")
    private val positionColumn = integer("position")
    private val lengthColumn = integer("length")

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(
        id: CommunityPostEntityId,
        target: Long,
        position: Int,
        length: Int,
    ) {
        insert { statement ->
            statement[idColumn] = id.long
            statement[targetColumn] = target
            statement[positionColumn] = position
            statement[lengthColumn] = length
        }
    }

    suspend fun selectByIds(
        ids: List<CommunityPostEntityId>,
    ): List<CommunityPostEntityMention> {
        val raw = ids.map { id -> id.long }
        return selectAll()
            .where { idColumn inList raw }
            .map { row ->
                CommunityPostEntityMention(
                    id = CommunityPostEntityId(row[idColumn]),
                    target = row[targetColumn],
                    position = row[positionColumn],
                    length = row[lengthColumn],
                )
            }
            .toList()
    }
}
