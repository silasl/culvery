package uk.co.siland.culvery.provider.calendar_google

/** What [block] threw, or null if it returned. Shared by this module's tests. */
internal suspend fun failureOf(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (e: Exception) {
        e
    }
