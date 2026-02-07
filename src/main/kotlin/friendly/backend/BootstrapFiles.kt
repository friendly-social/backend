package friendly.backend

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun bootstrapFiles(): FilesContext {
    val directory = Path(System.getenv("FRIENDLY_FILES_DIRECTORY"))
    val maxDirectorySize = System.getenv("FRIENDLY_FILES_LIMIT")
    val fileSystem = SystemFileSystem

    val resolvedDirectory = SystemFileSystem.resolve(directory)
    fileSystem.createDirectories(resolvedDirectory)

    return FilesContext(
        directory = resolvedDirectory,
        maxDirectorySize = FileSize.orThrow(maxDirectorySize.toLong()),
        fileSystem = fileSystem,
    )
}
