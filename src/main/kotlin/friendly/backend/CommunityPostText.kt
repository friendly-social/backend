package friendly.backend

import com.vdurmont.emoji.EmojiManager

data class CommunityPostText private constructor(val string: String) {
    val isEmoji: Boolean get() = EmojiManager.isEmoji(string)

    fun serializable(): CommunityPostTextSerializable =
        CommunityPostTextSerializable(string)

    companion object {
        val MaxLength: Int = 4096

        fun orThrow(string: String): CommunityPostText {
            require(string.length <= MaxLength) {
                "Community post can't be longer than $MaxLength"
            }
            return CommunityPostText(string)
        }
    }
}
