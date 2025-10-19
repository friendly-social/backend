package friendly.backend

import org.jetbrains.exposed.v1.core.Table

class FilesTable : Table("files") {
    val ownerId = long("owner_id")
}
