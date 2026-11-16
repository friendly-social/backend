data class Entry(val postDepth: Int)

val replies = listOf(
    Entry(postDepth = 0),
    Entry(postDepth = 3),
    Entry(postDepth = 1),
    Entry(postDepth = 3),
)

fun threadBroken(): Boolean {
    var lastDepth: Int? = null
    for (post in replies.sortedBy { entry -> entry.postDepth }) {
        if (lastDepth == post.postDepth) {
            return false
        }
        lastDepth = post.postDepth
    }
    return true
}

println(threadBroken())
