package uk.co.siland.culvery.capability.calendar_testkit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.JUnitCore
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.GoodContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.InclusiveAllDayEndContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.LeakingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.PartialFirstSyncContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RawAuthErrorContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RawErrorContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RepeatingContract

/** Proves the suite passes a correct provider and catches each kind of broken one. */
class ContractSuiteSelfTest {
    /** Names of the failed checks; each must have failed on an assertion, not crashed. */
    private fun failuresOf(contract: Class<*>): List<String> {
        val failures = JUnitCore.runClasses(contract).failures
        failures.forEach { f ->
            assertWithMessage("${f.description.methodName} should fail an assertion, not crash:\n${f.trace}")
                .that(f.exception).isInstanceOf(AssertionError::class.java)
        }
        return failures.map { it.description.methodName }
    }

    @Test
    fun wellBehavedProviderPassesEveryCheck() {
        val result = JUnitCore.runClasses(GoodContract::class.java)
        assertThat(result.failures.map { "${it.description.methodName}: ${it.message}" }).isEmpty()
        assertThat(result.runCount).isEqualTo(10)
        // A skipped check would otherwise count as a pass.
        assertThat(result.assumptionFailureCount).isEqualTo(0)
    }

    @Test
    fun leakedOutOfRangeEventIsCaught() {
        assertThat(failuresOf(LeakingContract::class.java)).containsExactly("syncReturnsOnlyEventsOverlappingRange")
    }

    @Test
    fun partialFirstSyncIsCaught() {
        assertThat(failuresOf(PartialFirstSyncContract::class.java)).containsExactly("firstSyncIsFullReplace")
    }

    @Test
    fun repeatingUnchangedEventsOnACursorIsCaught() {
        assertThat(failuresOf(RepeatingContract::class.java))
            .containsExactly("syncWithReturnedCursorDoesNotRepeatUnchangedEvents")
    }

    @Test
    fun rawNetworkExceptionIsCaught() {
        assertThat(failuresOf(RawErrorContract::class.java)).containsExactly("networkFailureThrowsUnreachable")
    }

    @Test
    fun inclusiveAllDayEndIsCaught() {
        assertThat(failuresOf(InclusiveAllDayEndContract::class.java)).containsExactly("allDayEventsUseExclusiveEndDates")
    }

    @Test
    fun rawAuthExceptionIsCaught() {
        assertThat(failuresOf(RawAuthErrorContract::class.java)).containsExactly("authFailureThrowsNeedsSignIn")
    }
}
