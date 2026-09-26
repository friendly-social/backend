package friendly.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Instant

object FilesTable : Table("files") {
    val idColumn = long("id").autoIncrement()
    val ownerIdColumn = long("owner_id")
    val sizeColumn = long("size")
    val instantColumn = timestamp("instant")
    val accessHashColumn = varchar("access_hash", FileAccessHash.Length)

    val pendingColumn = bool("pending")
    val markForDeletionColumn = bool("mark_for_deletion").default(false)

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(
        ownerId: UserId,
        size: FileSize,
        instant: Instant,
        accessHash: FileAccessHash,
        pending: Boolean,
    ): FileId {
        val long = insert { statement ->
            statement[ownerIdColumn] = ownerId.long
            statement[sizeColumn] = size.bytes
            statement[instantColumn] = instant
            statement[accessHashColumn] = accessHash.string
            statement[pendingColumn] = pending
        }[idColumn]
        return FileId(long)
    }

    suspend fun selectFilesSize(
        ownerId: UserId?,
        after: Instant?,
        pending: Boolean?,
        markForDeletion: Boolean?,
    ): FileSize {
        val sizeSum = sizeColumn.sum()
        var where: Op<Boolean> = Op.TRUE
        if (ownerId != null) {
            where = where and (ownerIdColumn eq ownerId.long)
        }
        if (after != null) {
            where = where and (instantColumn greater after)
        }
        if (pending != null) {
            where = where and (pendingColumn eq pending)
        }
        if (markForDeletion != null) {
            where = where and (markForDeletionColumn eq markForDeletion)
        }
        return select(sizeSum)
            .where(where)
            .single()
            .get(sizeSum)
            .let { sum -> FileSize.orThrow(sum ?: 0) }
    }

    suspend fun selectBefore(
        instant: Instant,
        pending: Boolean?,
        markForDeletion: Boolean?,
    ): Flow<FileId> {
        var condition: Op<Boolean> = instantColumn less instant
        if (pending != null) {
            condition = condition and (pendingColumn eq pending)
        }
        if (markForDeletion != null) {
            condition = condition and (markForDeletionColumn eq markForDeletion)
        }
        return selectAll()
            .where(condition)
            .map { row -> FileId(row[idColumn]) }
    }

    suspend fun selectForDeletion(): Flow<FileId> = selectAll()
        .where(markForDeletionColumn eq true)
        .map { row -> FileId(row[idColumn]) }

    suspend fun selectByIdOrNull(
        id: FileId,
        pending: Boolean?,
        markForDeletion: Boolean?,
    ): Entry? {
        var condition = idColumn eq id.long
        if (pending != null) {
            condition = condition and (pendingColumn eq pending)
        }
        if (markForDeletion != null) {
            condition = condition and (markForDeletionColumn eq markForDeletion)
        }
        return selectAll()
            .where(condition)
            .singleOrNull()
            ?.toEntry()
    }

    suspend fun markForDeletion(ids: List<FileId>) {
        val rawIds = ids.map(FileId::long)
        update({ idColumn inList rawIds }) { statement ->
            statement[markForDeletionColumn] = true
        }
    }

    suspend fun deleteMarkedForDeletion() {
        deleteWhere { markForDeletionColumn eq true }
    }

    suspend fun deletePending() {
        deleteWhere { pendingColumn eq true }
    }

    suspend fun deleteById(id: FileId) {
        deleteWhere { idColumn eq id.long }
    }

    suspend fun complete(id: FileId) {
        update({ idColumn eq id.long }) { statement ->
            statement[pendingColumn] = false
        }
    }

    suspend fun selectAccessHash(id: FileId): FileAccessHash? {
        val accessHash = select(accessHashColumn)
            .where(idColumn eq id.long)
            .firstOrNull()
            ?.get(accessHashColumn)
            ?: return null
        return FileAccessHash.orThrow(accessHash)
    }

    private fun ResultRow.toEntry(): Entry = Entry(
        id = FileId(this[idColumn]),
        ownerId = UserId(this[ownerIdColumn]),
        size = FileSize.orThrow(this[sizeColumn]),
        instant = this[instantColumn],
        accessHash = FileAccessHash.orThrow(this[accessHashColumn]),
        pending = this[pendingColumn],
        markForDeletion = this[markForDeletionColumn],
    )

    data class Entry(
        val id: FileId,
        val ownerId: UserId,
        val size: FileSize,
        val instant: Instant,
        val accessHash: FileAccessHash,
        val pending: Boolean,
        val markForDeletion: Boolean,
    )
}
