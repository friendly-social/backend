package friendly.backend

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

object UsersTable : Table("users") {
    private val idColumn = long("id").autoIncrement()
    private val accessHashColumn = varchar("access_hash", UserAccessHash.Length)
    private val nicknameColumn = varchar("nickname", Nickname.MaxLength)

    private val socialLinkColumn =
        varchar("social_link", SocialLink.MaxLength).nullable()

    private val descriptionColumn =
        varchar("description", UserDescription.MaxLength)

    private val avatarIdColumn = long("avatar_id").nullable()

    private val avatarAccessHashColumn =
        varchar("avatar_access_hash", FileAccessHash.Length).nullable()

    override val primaryKey = PrimaryKey(idColumn)

    suspend fun usedAsAvatars(ids: Set<FileId>): Set<FileId> {
        val rawIds = ids.map(FileId::long)
        return select(avatarIdColumn)
            .where(avatarIdColumn inList rawIds)
            .map { row -> FileId(row[avatarIdColumn]!!) }
            .toSet()
    }

    suspend fun insert(
        accessHash: UserAccessHash,
        nickname: Nickname,
        description: UserDescription,
        avatar: FileDescriptor?,
        socialLink: SocialLink?,
    ): UserId {
        val result = insert { statement ->
            statement[nicknameColumn] = nickname.string
            statement[descriptionColumn] = description.string
            statement[accessHashColumn] = accessHash.string
            statement[socialLinkColumn] = socialLink?.string
            if (avatar != null) {
                statement[avatarIdColumn] = avatar.id.long
                statement[avatarAccessHashColumn] = avatar.accessHash.string
            }
        }
        return UserId(result[idColumn])
    }

    suspend fun select(ids: List<UserId>): List<Entry?> {
        val results = selectAll()
            .where(idColumn inList ids.map(UserId::long))
            .map { row -> row.toEntry() }
            .toList()
            .associateBy(Entry::id)
        return ids.map { id -> results[id] }
    }

    suspend fun selectByDescriptor(descriptor: UserDescriptor): Entry? =
        selectAll()
            .where(
                (idColumn eq descriptor.id.long) and
                    (accessHashColumn eq descriptor.accessHash.string),
            )
            .map { row -> row.toEntry() }
            .firstOrNull()

    suspend fun update(
        id: UserId,
        nickname: Field<Nickname>?,
        description: Field<UserDescription>?,
        avatar: Field<FileDescriptor?>?,
        socialLink: Field<SocialLink?>?,
    ) {
        if (nothingChanged(nickname, description, avatar, socialLink)) {
            return
        }
        update(
            where = { idColumn eq id.long },
        ) { statement ->
            if (nickname != null) {
                statement[nicknameColumn] = nickname.value.string
            }
            if (description != null) {
                statement[descriptionColumn] = description.value.string
            }
            if (avatar != null) {
                val avatarId = avatar.value?.id?.long
                val avatarAccessHash = avatar.value?.accessHash?.string
                statement[avatarIdColumn] = avatarId
                statement[avatarAccessHashColumn] = avatarAccessHash
            }
            if (socialLink != null) {
                statement[socialLinkColumn] = socialLink.value?.string
            }
        }
    }

    private fun nothingChanged(vararg fields: Field<*>?): Boolean =
        fields.all { field ->
            field == null
        }

    private fun ResultRow.toEntry(): Entry {
        val avatarId = this[avatarIdColumn]
            ?.let(::FileId)
        val avatarAccessHash = this[avatarAccessHashColumn]
            ?.let(FileAccessHash::orThrow)
        return Entry(
            id = UserId(this[idColumn]),
            accessHash = UserAccessHash.orThrow(this[accessHashColumn]),
            nickname = Nickname.orThrow(this[nicknameColumn]),
            description = UserDescription.orThrow(this[descriptionColumn]),
            avatar = if (avatarId != null && avatarAccessHash != null) {
                FileDescriptor(avatarId, avatarAccessHash)
            } else {
                null
            },
            socialLink = this[socialLinkColumn]?.let(SocialLink::orThrow),
        )
    }

    data class Entry(
        val id: UserId,
        val accessHash: UserAccessHash,
        val nickname: Nickname,
        val description: UserDescription,
        val avatar: FileDescriptor?,
        val socialLink: SocialLink?,
    )
}
