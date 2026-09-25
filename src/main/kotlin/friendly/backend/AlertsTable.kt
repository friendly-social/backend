package friendly.backend

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.insert

object AlertsTable : Table("alerts") {
    val idColumn = long("id").autoIncrement()
    val typeColumn = enumeration<Type>("type")
    val instantColumn = timestamp("instant")
    val userIdColumn = long("user_id").nullable()

    suspend fun insert(payload: AlertPayload) {
        insert { statement ->
            statement[instantColumn] = payload.instant
            statement[userIdColumn] = payload.userId?.long
            when (payload) {
                is AlertPayload.SignUpAvatarNotFound -> {
                    statement[typeColumn] = Type.SignUpAvatarNotFound
                }
                is AlertPayload.Limit.FilesServiceSizeOverall -> {
                    statement[typeColumn] = Type.LimitFilesServiceSizeOverall
                }
                is AlertPayload.Limit.FilesServiceSizePerDay -> {
                    statement[typeColumn] = Type.LimitFilesServiceSizePerDay
                }
                is AlertPayload.Limit.FilesServiceDownloadPerMonth -> {
                    statement[typeColumn] =
                        Type.LimitFilesServiceDownloadPerMonth
                }
                is AlertPayload.Limit.FilesServiceUploadPerMonth -> {
                    statement[typeColumn] = Type.LimitFilesServiceUploadPerMonth
                }
                is AlertPayload.Limit.FilesUserSizePerDay -> {
                    statement[typeColumn] = Type.LimitFilesUserSizePerDay
                }
                is AlertPayload.Limit.FilesUserDownloadPerMonth -> {
                    statement[typeColumn] = Type.LimitFilesUserDownloadPerMonth
                }
                is AlertPayload.Limit.FilesUserUploadPerMonth -> {
                    statement[typeColumn] = Type.LimitFilesUserUploadPerMonth
                }
            }
        }
    }

    enum class Type {
        SignUpAvatarNotFound,
        LimitFilesServiceSizeOverall,
        LimitFilesServiceSizePerDay,
        LimitFilesServiceDownloadPerMonth,
        LimitFilesServiceUploadPerMonth,
        LimitFilesUserSizePerDay,
        LimitFilesUserDownloadPerMonth,
        LimitFilesUserUploadPerMonth,
    }
}
