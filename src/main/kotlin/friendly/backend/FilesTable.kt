package friendly.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Instant

/**
 * File insert has 2 states:
 *
 * * FileId is injected and no other information provided. That way it is
 *   possible to get fileId. ownerId and ownerIp is null.
 *
 * * File is uploaded and then information is ready to be filled.
 */
// todo: concurrent upload as of now will overthrow limits.
object FilesTable : Table("files") {
    val idColumn = long("id").autoIncrement()
    val pendingColumn = bool("pending")
    val markForDeletionColumn = bool("mark_for_deletion").default(false)

    // required fields when pending: false
    val timestampColumn = timestamp("timestamp").nullable()
    val accessHashColumn = varchar(
        "access_hash",
        FileAccessHash.Length,
    ).nullable()
    val sizeColumn = long("size").nullable()
    val ownerIpColumn = varchar("owner_ip", IpAddress.MaxLength).nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun selectFilesSize(ip: IpAddress): FileSize = selectAll()
        .where(ownerIpColumn eq ip.string)
        .toList()
        .sumOf { result -> result[sizeColumn] ?: 0 }
        .let(FileSize::orThrow)

    suspend fun selectFilesSize(): FileSize = selectAll()
        .toList()
        .sumOf { result -> result[sizeColumn] ?: 0 }
        .let(FileSize::orThrow)

    suspend fun selectBefore(instant: Instant): Flow<FileId> = selectAll()
        .where(timestampColumn less instant)
        .map { row -> FileId(row[idColumn]) }

    suspend fun selectForDeletion(): Flow<FileId> = selectAll()
        .where(markForDeletionColumn eq true)
        .map { row -> FileId(row[idColumn]) }

    suspend fun markForDeletion(ids: List<FileId>) {
        val rawIds = ids.map(FileId::long)
        update({ idColumn inList rawIds }) { statement ->
            statement[markForDeletionColumn] = true
        }
    }

    suspend fun deleteMarked() {
        deleteWhere { markForDeletionColumn eq true }
    }

    suspend fun insert(): FileId {
        val long = insert { statement ->
            statement[pendingColumn] = true
        }[idColumn]
        return FileId(long)
    }

    suspend fun complete(
        id: FileId,
        instant: Instant,
        accessHash: FileAccessHash,
        size: FileSize,
        ownerIp: IpAddress,
    ) {
        update({ idColumn eq id.long }) { statement ->
            statement[timestampColumn] = instant
            statement[accessHashColumn] = accessHash.string
            statement[sizeColumn] = size.bytes
            statement[ownerIpColumn] = ownerIp.string
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
}
