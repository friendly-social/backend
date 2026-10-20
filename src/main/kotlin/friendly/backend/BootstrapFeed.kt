package friendly.backend

suspend fun bootstrapFeed(): FeedContext {
    val enableExtendedNetwork =
        System.getenv("FRIENDLY_ENABLE_EXTENDED_NETWORK") != null

    return FeedContext(enableExtendedNetwork)
}
