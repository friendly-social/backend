package friendly.backend

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlin.time.Duration

data class FilesContext(
    val directory: Path,
    val fileSystem: FileSystem,
    val maxDirectorySize: FileSize,
    val cleanupInterval: Duration,
    val cleanupDelay: Duration,
)
