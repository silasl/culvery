package uk.co.siland.culvery.core.google

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Test
import uk.co.siland.culvery.core.plugin.OfflineException

class GoogleApiTest {
    private val service = GoogleService("GoogleTest", "Google Test")

    private fun forbidden(reason: String) = GoogleAnswer(403, """{"error":{"errors":[{"reason":"$reason"}]}}""")

    @Test
    fun aMissingScopeIsToldFromARateLimit() {
        assertThat(forbidden("insufficientPermissions").fault()).isEqualTo(GoogleFault.SCOPE_MISSING)
        assertThat(forbidden("quotaExceeded").fault()).isEqualTo(GoogleFault.TRY_LATER)
        assertThat(GoogleAnswer(503, "").fault()).isEqualTo(GoogleFault.TRY_LATER)
        assertThat(GoogleAnswer(404, "").fault()).isNull()
    }

    @Test
    fun aProvidersOwnRateLimitReasonCountsOnlyWhenItAsksForIt() {
        assertThat(forbidden("calendarUsageLimitsExceeded").fault()).isNull()
        assertThat(forbidden("calendarUsageLimitsExceeded").fault(setOf("calendarUsageLimitsExceeded"))).isEqualTo(GoogleFault.TRY_LATER)
    }

    @Test
    fun noNetworkIsOfflineOnlyWhenTheServiceSaysSo() {
        val noHost = UnknownHostException("no.example")
        assertThat(noHost.asUnreachable(service, offlineOnUnknownHost = true)).isInstanceOf(OfflineException::class.java)
        assertThat(noHost.asUnreachable(service, offlineOnUnknownHost = false)).isNotInstanceOf(OfflineException::class.java)
        assertThat(IOException("reset").asUnreachable(service, offlineOnUnknownHost = true)).isNotInstanceOf(OfflineException::class.java)
        assertThat(noHost.asUnreachable(service, offlineOnUnknownHost = true)).hasMessageThat().isEqualTo("No network to Google Test")
    }
}
