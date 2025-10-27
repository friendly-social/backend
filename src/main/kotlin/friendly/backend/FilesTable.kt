package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
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
    val id = long("id").autoIncrement()
    val pending = bool("pending")

    // required fields when pending: false
    val timestamp = timestamp("timestamp").nullable()
    val accessHash = varchar("access_hash", FileAccessHash.Length).nullable()
    val size = long("size").nullable()
    val ownerIp = varchar("owner_ip", IpAddress.MaxLength).nullable()

    override val primaryKey = PrimaryKey(id)

    suspend fun impureSelectFilesSize(ip: IpAddress): FileSize = selectAll()
        .where(ownerIp eq ip.string)
        .toList()
        .sumOf { result -> result[size] ?: 0 }
        .let(FileSize::orThrow)

    suspend fun impureSelectFilesSize(): FileSize = selectAll()
        .toList()
        .sumOf { result -> result[size] ?: 0 }
        .let(FileSize::orThrow)

    suspend fun impureInsert(): FileId {
        val long = insert { statement ->
            statement[pending] = true
        }[id]
        return FileId(long)
    }

    suspend fun impureUpdate(
        id: FileId,
        instant: Instant,
        accessHash: FileAccessHash,
        size: FileSize,
        ownerIp: IpAddress,
    ) {
        update({ this.id eq id.long }) { statement ->
            statement[this.timestamp] = instant
            statement[this.accessHash] = accessHash.string
            statement[this.size] = size.bytes
            statement[this.ownerIp] = ownerIp.string
        }
    }

    suspend fun impureSelectAccessHash(id: FileId): FileAccessHash? {
        val accessHash = select(this.accessHash)
            .where(this.id eq id.long)
            .firstOrNull()
            ?.get(this.accessHash)
            ?: return null
        return FileAccessHash.orThrow(accessHash)
    }
}
