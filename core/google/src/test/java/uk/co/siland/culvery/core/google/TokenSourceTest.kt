package uk.co.siland.culvery.core.google

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.google.test.FakeAuthorizer
import uk.co.siland.culvery.core.google.test.granted
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OfflineException
import uk.co.siland.culvery.core.plugin.UnreachableException

// Robolectric for PendingIntent.
@RunWith(AndroidJUnit4::class)
class TokenSourceTest {
    private val authorizer = FakeAuthorizer()
    private val tokens = PlayServicesTokenSource(authorizer)
    private val scopes = listOf("s1", "s2")

    private fun screens(): PendingIntent =
        PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

    @Test
    fun aGrantIsTheTokenForThatAccount() = runTest {
        authorizer.next = granted("t1", scopes)
        assertThat(tokens.token("family@example.com", scopes)).isEqualTo("t1")
        assertThat(authorizer.accounts).containsExactly("family@example.com")
    }

    @Test
    fun aTokenIsAskedForTheGivenScopes() = runTest {
        tokens.token("family@example.com", listOf("s1"))
        assertThat(authorizer.accounts).containsExactly("family@example.com")
        assertThat(authorizer.scopesAsked).containsExactly(listOf("s1"))
    }

    @Test
    fun aGrantNeverPrintsItsToken() {
        assertThat(granted("secret-token", scopes).toString()).doesNotContain("secret-token")
    }

    @Test
    fun screensTheUserMustSeeMeanTheConnectionNeedsSignIn() = runTest {
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(failureOf { tokens.token("family@example.com", scopes) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun anUnreachablePlayServicesIsUnreachable() = runTest {
        authorizer.failWith = UnreachableException("offline")
        assertThat(failureOf { tokens.token("family@example.com", scopes) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun onlyAStatusThatNeedsTheUserMeansSignInAndAnyOtherIsTryLaterNamingIt() {
        listOf(CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.INVALID_ACCOUNT, CommonStatusCodes.RESOLUTION_REQUIRED).forEach {
            assertWithMessage("status $it").that(authorizationFailure(it)).isInstanceOf(NeedsSignInException::class.java)
        }
        listOf(
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.API_NOT_CONNECTED, CommonStatusCodes.DEVELOPER_ERROR, CommonStatusCodes.CANCELED,
        ).forEach { status ->
            val failure = authorizationFailure(status)
            assertWithMessage("status $status").that(failure).isInstanceOf(UnreachableException::class.java)
            // The sync logs it with its cause, so the status is in the log.
            assertWithMessage("status $status").that(failure.message).contains("status $status")
        }
        assertThat(authorizationFailure(CommonStatusCodes.CANCELED).isUserCancel()).isTrue()
        assertThat(authorizationFailure(CommonStatusCodes.INTERNAL_ERROR).isUserCancel()).isFalse()
    }

    @Test
    fun invalidatingClearsTheTokenInPlayServices() = runTest {
        tokens.invalidate("t1")
        assertThat(authorizer.cleared).containsExactly("t1")
    }

    /** 5 §A.3: Play services saying there is no network is the tablet offline, not Google not answering. */
    @Test
    fun playServicesWithNoNetworkIsOffline() {
        assertThat(authorizationFailure(CommonStatusCodes.NETWORK_ERROR)).isInstanceOf(OfflineException::class.java)
        assertThat(authorizationFailure(CommonStatusCodes.INTERNAL_ERROR)).isInstanceOf(PlayServicesStatusException::class.java)
    }
}
