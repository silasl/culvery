package uk.co.siland.culvery.capability.hoover

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.household.Role

/** 11 §4.4: Start asks for a fresh Adult or Admin PIN every time, and a Child's PIN starts nothing. */
class HooverActionsTest {
    private val system = FakeHooverSystem()

    private fun actions(access: TestAccess) = HooverActions(access, setOf(system))

    /** Fails if an Adult's PIN doesn't start the hoover or the system's answer is lost. */
    @Test
    fun anAdultsPinStarts() = runTest {
        system.next = HooverResult.NotStarted
        val access = TestAccess(Role.ADULT)
        assertThat(actions(access).start()).isEqualTo(HooverResult.NotStarted)
        assertThat(access.asked).containsExactly(listOf("hoover.start"))
        assertThat(system.starts).isEqualTo(1)
    }

    @Test
    fun anAdminsPinStarts() = runTest {
        val access = TestAccess(Role.ADMIN)
        assertThat(actions(access).start()).isEqualTo(HooverResult.Started)
        assertThat(access.asked).containsExactly(listOf("hoover.start"))
        assertThat(system.starts).isEqualTo(1)
    }

    /** Review Focus 3. Fails if a Child's PIN reaches the hoover. */
    @Test
    fun aChildsPinStartsNothing() = runTest {
        val access = TestAccess(Role.CHILD)
        assertThat(actions(access).start()).isNull()
        assertThat(access.asked).containsExactly(listOf("hoover.start"))
        assertThat(system.starts).isEqualTo(0)
    }

    @Test
    fun aCancelledPadStartsNothing() = runTest {
        val access = TestAccess(null)
        assertThat(actions(access).start()).isNull()
        assertThat(access.asked).containsExactly(listOf("hoover.start"))
        assertThat(system.starts).isEqualTo(0)
    }

    /** Fails if a second start rides on the first PIN. */
    @Test
    fun everyStartAsksAgain() = runTest {
        val access = TestAccess(Role.ADULT)
        val actions = actions(access)
        actions.start()
        actions.start()
        assertThat(access.asked).hasSize(2)
        assertThat(system.starts).isEqualTo(2)
    }

    /** Pin: with no hoover chosen there is nothing to ask a PIN for. */
    @Test
    fun noHooverAsksNothing() = runTest {
        val access = TestAccess(Role.ADULT)
        assertThat(HooverActions(access, emptySet()).start()).isNull()
        assertThat(access.asked).isEmpty()
    }
}
