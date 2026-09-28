package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException

// Robolectric for PendingIntent.
@RunWith(AndroidJUnit4::class)
class TokenSourceTest {
    private val authorizer = FakeAuthorizer()
    private val tokens = PlayServicesTokenSource(authorizer)

    @Test
    fun aGrantIsTheTokenForThatAccount() = runTest {
        authorizer.next = granted("t1")
        assertThat(tokens.token("family@example.com")).isEqualTo("t1")
        assertThat(authorizer.accounts).containsExactly("family@example.com")
    }

    @Test
    fun aGrantNeverPrintsItsToken() {
        assertThat(granted("secret-token").toString()).doesNotContain("secret-token")
    }

    @Test
    fun screensTheUserMustSeeMeanTheConnectionNeedsSignIn() = runTest {
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(failureOf { tokens.token("family@example.com") }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun anUnreachablePlayServicesIsUnreachable() = runTest {
        authorizer.failWith = UnreachableException("offline")
        assertThat(failureOf { tokens.token("family@example.com") }).isInstanceOf(UnreachableException::class.java)
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
}
