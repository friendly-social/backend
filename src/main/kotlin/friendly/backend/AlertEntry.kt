package friendly.backend

import kotlin.time.Instant

sealed interface AlertEntry {
    val id: ActivityId
    val instant: Instant

    sealed interface Limit : AlertEntry {
        data class FilesServiceSizeOverall(
            override val id: ActivityId,
            override val instant: Instant,
        ) : Limit
        data class FilesServiceSizePerDay(
            override val id: ActivityId,
            override val instant: Instant,
        ) : Limit
        data class FilesServiceDownloadPerMonth(
            override val id: ActivityId,
            override val instant: Instant,
        ) : Limit
        data class FilesServiceUploadPerMonth(
            override val id: ActivityId,
            override val instant: Instant,
        ) : Limit

        data class FilesUserSizePerDay(
            override val id: ActivityId,
            override val instant: Instant,
            val userId: UserId,
        ) : Limit
        data class FilesUserDownloadReached(
            override val id: ActivityId,
            override val instant: Instant,
            val userId: UserId,
        ) : Limit
        data class FilesUserUploadReached(
            override val id: ActivityId,
            override val instant: Instant,
            val userId: UserId,
        ) : Limit
    }
}
