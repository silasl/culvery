package uk.co.siland.culvery.core.google

import javax.inject.Inject
import uk.co.siland.culvery.core.plugin.NeedsSignInException

/** Short-lived access tokens for a Google account (3a design D2). Culvery stores none. */
interface TokenSource {
    /**
     * A token for [account] carrying [scopes]: throws NeedsSignInException when the user must act, UnreachableException
     * when offline.
     */
    suspend fun token(account: String, scopes: List<String>): String

    /** Google refused [token]: clear it from the cache so the next [token] is fresh. */
    suspend fun invalidate(token: String)
}

/**
 * Asks Play services for a token on every call (3a design §3.2): it caches and refreshes them itself. Screens the user
 * must see mean the connection needs signing in again.
 */
class PlayServicesTokenSource @Inject constructor(private val authorizer: Authorizer) : TokenSource {
    override suspend fun token(account: String, scopes: List<String>): String =
        when (val answer = authorizer.authorize(account, scopes)) {
            is Authorization.Granted -> answer.token
            is Authorization.NeedsUser -> throw NeedsSignInException("Google needs the user to sign in again")
        }

    override suspend fun invalidate(token: String) = authorizer.clearToken(token)
}
