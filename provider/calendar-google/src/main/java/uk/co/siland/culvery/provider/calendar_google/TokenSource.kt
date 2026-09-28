package uk.co.siland.culvery.provider.calendar_google

/** Short-lived access tokens for a Google account (3a design D2). Culvery stores none. */
interface TokenSource {
    /** A token for [account]: throws NeedsSignInException when the user must act, UnreachableException when offline. */
    suspend fun token(account: String): String

    /** Google refused [token]: clear it from the cache so the next [token] is fresh. */
    suspend fun invalidate(token: String)
}
