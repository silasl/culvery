package uk.co.siland.culvery.capability.calendar_testkit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.JUnitCore
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.BlindContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.DroppingTagsContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.DuplicatingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.GoodContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.InclusiveAllDayEndContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.KeylessContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.LeakingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.OverwritingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.PartialFirstSyncContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RawAuthErrorContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RawErrorContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.ReadOnlyContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RecreatingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RepeatingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.StrictDeleteContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.TwoPrimariesContract

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
        assertThat(result.runCount).isEqualTo(21)
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

    @Test
    fun droppedTagsAreCaught() {
        // update() has no field for createdBy (3a design C3: no update ever changes it), so a provider that drops the
        // creator tag at create time never gets it back, and updatedFieldsRoundTrip's tag check fails too.
        assertThat(failuresOf(DroppingTagsContract::class.java))
            .containsExactly("createdEventComesBackOnTheNextSyncWithItsTags", "updatedFieldsRoundTrip")
    }

    @Test
    fun refusingToDeleteAMissingEventIsCaught() {
        assertThat(failuresOf(StrictDeleteContract::class.java)).containsExactly("deletingAnEventThatIsAlreadyGoneSucceeds")
    }

    @Test
    fun aReadOnlyProviderSkipsOnlyTheWriteChecks() {
        val result = JUnitCore.runClasses(ReadOnlyContract::class.java)
        assertThat(result.failures.map { "${it.description.methodName}: ${it.message}" }).isEmpty()
        assertThat(result.runCount).isEqualTo(21)
        assertThat(result.assumptionFailureCount).isEqualTo(10)
    }

    @Test
    fun aWriterThatIgnoresTheClientKeyIsCaught() {
        assertThat(failuresOf(KeylessContract::class.java)).containsExactly("aRepeatedCreateWithTheSameKeyReturnsTheSameEvent")
    }

    @Test
    fun aWriterThatMakesASecondEventForARepeatedKeyIsCaught() {
        assertThat(failuresOf(DuplicatingContract::class.java)).containsExactly("aRepeatedCreateWithTheSameKeyReturnsTheSameEvent")
    }

    @Test
    fun aWriterThatRecreatesADeletedEventIsCaught() {
        assertThat(failuresOf(RecreatingContract::class.java)).containsExactly("aCreateNeverRecreatesADeletedEvent")
    }

    @Test
    fun anUpdateThatChangesEveryFieldIsCaught() {
        assertThat(failuresOf(OverwritingContract::class.java)).containsExactly("anUpdateChangesOnlyItsFields")
    }

    @Test
    fun aWriterThatCannotFindItsEventsIsCaught() {
        assertThat(failuresOf(BlindContract::class.java)).containsExactly("findReturnsACreatedEventAndNullOnceItIsDeleted")
    }

    @Test
    fun twoPrimaryCalendarsAreCaught() {
        assertThat(failuresOf(TwoPrimariesContract::class.java)).containsExactly("sourcesReportAtMostOnePrimary")
    }
}
