package friendly.backend.communitypostentity

import friendly.backend.CommunityPostId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

object CommunityPostEntitiesRelationTable : Table("community_post_entities_relation") {
    private val postIdColumn = long("post_id")
    private val entityIdColumn = long("entity_id")

    override val primaryKey = PrimaryKey(postIdColumn, entityIdColumn)

    suspend fun insert(postId: CommunityPostId, entityId: CommunityPostEntityId) {
        insert { statement ->
            statement[postIdColumn] = postId.long
            statement[entityIdColumn] = entityId.long
        }
    }

    suspend fun selectEntityIds(
        postId: CommunityPostId,
    ): List<CommunityPostEntityId> = selectAll()
        .where { postIdColumn eq postId.long }
        .map { row -> CommunityPostEntityId(row[entityIdColumn]) }
        .toList()

    suspend fun selectEntityIds(
        postIds: List<CommunityPostId>,
    ): Map<CommunityPostId, List<CommunityPostEntityId>> {
        val raw = postIds.map { id -> id.long }
        return selectAll()
            .where { postIdColumn inList raw }
            .toList()
            .groupBy(
                keySelector = { row -> CommunityPostId(row[postIdColumn]) },
                valueTransform = { row -> CommunityPostEntityId(row[entityIdColumn]) },
            )
    }
}
