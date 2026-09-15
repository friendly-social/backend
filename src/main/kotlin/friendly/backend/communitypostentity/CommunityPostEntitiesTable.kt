package friendly.backend.communitypostentity

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object CommunityPostEntitiesTable : Table("community_post_entities") {
    private val idColumn = long("id").autoIncrement()
    private val typeColumn = enumeration<Type>("type")

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(type: Type): CommunityPostEntityId {
        val statement = insert { statement ->
            statement[typeColumn] = type
        }
        return CommunityPostEntityId(statement[idColumn])
    }

    data class Entry(val id: CommunityPostEntityId, val type: Type)

    suspend fun selectByIds(ids: List<CommunityPostEntityId>): List<Entry> {
        val raw = ids.map { id -> id.long }
        return selectAll()
            .where { idColumn inList raw }
            .map { row -> Entry(CommunityPostEntityId(row[idColumn]), row[typeColumn]) }
            .toList()
    }

    enum class Type {
        Mention,
    }
}

suspend fun CommunityPostEntitiesTable.selectTyped(
    ids: List<CommunityPostEntityId>,
): List<CommunityPostEntity> {
    val entries = selectByIds(ids)
    val mentions = entries
        .filter { entry -> entry.type == CommunityPostEntitiesTable.Type.Mention }
        .map { entry -> entry.id }
        .let { mentionIds -> CommunityPostEntityMentionsTable.selectByIds(mentionIds) }
        .associateBy { mention -> mention.id }
    return entries.map { entry ->
        when (entry.type) {
            CommunityPostEntitiesTable.Type.Mention -> mentions.getValue(entry.id)
        }
    }
}
