package friendly.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Instant

object FilesPreuploadTable : Table("files_preupload") {
    val idColumn = long("id").autoIncrement()
    val sizeColumn = long("size")
    val instantColumn = timestamp("instant")
    val accessHashColumn =
        varchar("access_hash", FilePreuploadAccessHash.Length)

    val pendingColumn = bool("pending").default(true)
    val markForDeletionColumn = bool("mark_for_deletion").default(false)

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun insert(
        size: FileSize,
        instant: Instant,
        accessHash: FilePreuploadAccessHash,
    ): FilePreuploadId {
        val long = insert { statement ->
            statement[sizeColumn] = size.bytes
            statement[instantColumn] = instant
            statement[accessHashColumn] = accessHash.string
        }[idColumn]
        return FilePreuploadId(long)
    }

    suspend fun selectFilesSize(
        pending: Boolean?,
        markForDeletion: Boolean?,
    ): FileSize {
        var where: Op<Boolean> = Op.TRUE
        if (pending != null) {
            where = where and (pendingColumn eq pending)
        }
        if (markForDeletion != null) {
            where = where and (markForDeletionColumn eq markForDeletion)
        }
        val sizeSum = sizeColumn.sum()
        return select(sizeSum)
            .where(where)
            .singleOrNull()
            ?.get(sizeSum)
            .let { long -> FileSize.orThrow(long ?: 0) }
    }

    suspend fun selectOldFiles(
        pending: Boolean?,
        markForDeletion: Boolean?,
    ): Flow<Entry> {
        var where: Op<Boolean> = Op.TRUE
        if (pending != null) {
            where = where and (pendingColumn eq pending)
        }
        if (markForDeletion != null) {
            where = where and (markForDeletionColumn eq markForDeletion)
        }
        return selectAll()
            .where(where)
            .map { row -> row.toEntry() }
    }

    suspend fun selectForDeletion(): Flow<FilePreuploadId> = selectAll()
        .where(markForDeletionColumn eq true)
        .map { row -> FilePreuploadId(row[idColumn]) }

    suspend fun selectByIdOrNull(
        id: FilePreuploadId,
        pending: Boolean,
        markForDeletion: Boolean,
    ): Entry? = selectAll()
        .where(
            (idColumn eq id.long) and (pendingColumn eq pending) and
                (markForDeletionColumn eq markForDeletion),
        )
        .singleOrNull()
        ?.toEntry()

    suspend fun markForDeletion(ids: List<FilePreuploadId>) {
        val rawIds = ids.map(FilePreuploadId::long)
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

    suspend fun deleteById(id: FilePreuploadId) {
        deleteWhere { idColumn eq id.long }
    }

    suspend fun complete(id: FilePreuploadId) {
        update({ idColumn eq id.long }) { statement ->
            statement[pendingColumn] = false
        }
    }

    private fun ResultRow.toEntry(): Entry = Entry(
        id = FilePreuploadId(this[idColumn]),
        size = FileSize.orThrow(this[sizeColumn]),
        instant = this[instantColumn],
        accessHash = FilePreuploadAccessHash.orThrow(this[accessHashColumn]),
        pending = this[pendingColumn],
        markForDeletion = this[markForDeletionColumn],
    )

    data class Entry(
        val id: FilePreuploadId,
        val size: FileSize,
        val instant: Instant,
        val accessHash: FilePreuploadAccessHash,
        val pending: Boolean,
        val markForDeletion: Boolean,
    )
}
