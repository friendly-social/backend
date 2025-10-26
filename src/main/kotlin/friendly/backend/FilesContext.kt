package friendly.backend

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

data class FilesContext(
    val directory: Path,
    val fileSystem: FileSystem,
    val maxDirectorySize: FileSize,
)
