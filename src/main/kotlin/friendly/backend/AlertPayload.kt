package friendly.backend

import kotlin.time.Instant

sealed interface AlertPayload {
    val instant: Instant
    val userId: UserId?

    data class SignUpAvatarNotFound(override val instant: Instant) :
        AlertPayload {
        override val userId: Nothing? get() = null
    }

    sealed interface Limit : AlertPayload {
        data class FilesServiceSizeOverall(override val instant: Instant) :
            Limit {
            override val userId: Nothing? get() = null
        }
        data class FilesServiceSizePerDay(override val instant: Instant) :
            Limit {
            override val userId: Nothing? get() = null
        }
        data class FilesServiceDownloadPerMonth(
            override val instant: Instant,
        ) : Limit {
            override val userId: Nothing? get() = null
        }
        data class FilesServiceUploadPerMonth(override val instant: Instant) :
            Limit {
            override val userId: Nothing? get() = null
        }

        data class FilesUserSizePerDay(
            override val instant: Instant,
            override val userId: UserId,
        ) : Limit
        data class FilesUserDownloadPerMonth(
            override val instant: Instant,
            override val userId: UserId,
        ) : Limit
        data class FilesUserUploadPerMonth(
            override val instant: Instant,
            override val userId: UserId,
        ) : Limit
    }
}
