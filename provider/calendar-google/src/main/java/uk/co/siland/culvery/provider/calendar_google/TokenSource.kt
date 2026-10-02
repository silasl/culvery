package uk.co.siland.culvery.provider.calendar_google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException

/** Short-lived access tokens for a Google account (3a design D2). Culvery stores none. */
interface TokenSource {
    /** A token for [account]: throws NeedsSignInException when the user must act, UnreachableException when offline. */
    suspend fun token(account: String): String

    /** Google refused [token]: clear it from the cache so the next [token] is fresh. */
    suspend fun invalidate(token: String)
}

/** The calendar scopes Culvery asks for (3a design D2): every calendar read, events written. */
internal val CALENDAR_SCOPES = listOf(
    "https://www.googleapis.com/auth/calendar.readonly",
    "https://www.googleapis.com/auth/calendar.events",
)

private const val GOOGLE_ACCOUNT_TYPE = "com.google"

/** The statuses that say the user must act (3a design D2); any other says nothing about the grant. */
private val NEEDS_USER_STATUSES =
    setOf(CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.INVALID_ACCOUNT, CommonStatusCodes.RESOLUTION_REQUIRED)

/** Play services' answer: a token with the scopes it grants, or screens (the account chooser, Google's consent) the user must see first. */
sealed interface Authorization {
    /** A plain class, not a data class: its toString must never print the token. */
    class Granted(val token: String, val scopes: List<String>) : Authorization

    class NeedsUser(val intent: PendingIntent) : Authorization
}

/** Play services' AuthorizationClient behind a seam, so tests need no Play services (3a design §3.2). */
interface Authorizer {
    /** Asks for the calendar scopes; for [account] when known, so the chooser is skipped and the token is its. */
    suspend fun authorize(account: String?): Authorization

    /** The answer the screens returned in [data]. */
    fun authorizationFrom(data: Intent?): Authorization

    suspend fun clearToken(token: String)
}

/**
 * Play services answered [statusCode], which doesn't say the user must act (Play services updating, an internal error,
 * a developer error, a cancel): try later. The status is in the message, so whoever logs it logs the status.
 */
internal class PlayServicesStatusException(val statusCode: Int, cause: Throwable?) :
    UnreachableException("Play services answered status $statusCode", cause)

/**
 * A Play services failure (3a design §3.2, D2): NeedsSignIn only for a status that says the user must act; any other
 * is Unreachable during a sync, and "Couldn't connect" when connecting.
 */
internal fun authorizationFailure(statusCode: Int, cause: Throwable? = null): Exception =
    if (statusCode in NEEDS_USER_STATUSES) {
        NeedsSignInException("Play services needs the user to sign in (status $statusCode)", cause)
    } else {
        PlayServicesStatusException(statusCode, cause)
    }

/** The person backed out of Play services' screens: the connect flow stops without a word. */
internal fun Throwable.isUserCancel(): Boolean = this is PlayServicesStatusException && statusCode == CommonStatusCodes.CANCELED

@Singleton
class PlayServicesAuthorizer @Inject constructor(@param:ApplicationContext private val context: Context) : Authorizer {
    // Built on the first call: Hilt makes this class on the main thread while the shell starts (4c §4.1).
    private val client by lazy { Identity.getAuthorizationClient(context) }

    override suspend fun authorize(account: String?): Authorization {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(CALENDAR_SCOPES.map(::Scope))
            .apply { if (account != null) setAccount(Account(account, GOOGLE_ACCOUNT_TYPE)) }
            .build()
        return playServices { client.authorize(request).await() }.toAuthorization()
    }

    override fun authorizationFrom(data: Intent?): Authorization =
        playServices { client.getAuthorizationResultFromIntent(data) }.toAuthorization()

    override suspend fun clearToken(token: String) {
        playServices { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    private fun AuthorizationResult.toAuthorization(): Authorization {
        val screens = pendingIntent
        if (hasResolution() && screens != null) return Authorization.NeedsUser(screens)
        // Neither screens nor a token says nothing about the grant (D2): try later.
        return Authorization.Granted(accessToken ?: throw UnreachableException("Play services granted no token"), grantedScopes)
    }
}

/** [call]'s failures in the tablet's terms: a Play services status as [authorizationFailure] maps it, anything else try later. */
private inline fun <T> playServices(call: () -> T): T =
    try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        throw authorizationFailure(e.statusCode, e)
    } catch (e: Exception) {
        throw UnreachableException("Play services failed (${e::class.simpleName})", e)
    }

/**
 * Asks Play services for a token on every call (3a design §3.2): it caches and refreshes them itself. Screens the user
 * must see mean the connection needs signing in again.
 */
class PlayServicesTokenSource @Inject constructor(private val authorizer: Authorizer) : TokenSource {
    override suspend fun token(account: String): String = when (val answer = authorizer.authorize(account)) {
        is Authorization.Granted -> answer.token
        is Authorization.NeedsUser -> throw NeedsSignInException("Google needs the user to sign in again")
    }

    override suspend fun invalidate(token: String) = authorizer.clearToken(token)
}
