package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pin: the calendar's exception names are the shared ones in :core:plugin, so what :core:google throws is caught as before. */
class CalendarContractTest {
    @Test
    fun theCalendarsExceptionsAreTheSharedOnes() {
        assertThat(NeedsSignInException("x")).isInstanceOf(uk.co.siland.culvery.core.plugin.NeedsSignInException::class.java)
        assertThat(OfflineException("x")).isInstanceOf(uk.co.siland.culvery.core.plugin.UnreachableException::class.java)
    }
}
