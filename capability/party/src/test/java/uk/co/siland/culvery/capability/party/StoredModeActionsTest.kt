package uk.co.siland.culvery.capability.party

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.TURN_OFF_NEEDS_A_PIN
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.ModeRefusal
import uk.co.siland.culvery.core.household.Role

/** 15b §4.3: one tap on Home, a fresh PIN only where the mode needs one, and one mode at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StoredModeActionsTest {
    private val holiday = MutableStateFlow<Boolean?>(false)
    private val holidayTurningOn = MutableStateFlow(false)

    private fun TestScope.rig() = HouseRig(backgroundScope)

    private fun actions(access: FakeAccess, rig: HouseRig) =
        StoredModeActions(access, rig.house, rig.settings, setOf(TestSwitch(HouseModes.HOLIDAY, "Holiday mode", holiday, turningOn = holidayTurningOn), rig.house))

    /** Party mode needing a PIN, Film night not, both with nothing to do but Film night's music and lights. */
    private suspend fun HouseRig.withFilmNight(film: StoredMode = FILM_NIGHT) = choose(PARTY_MODE, film).also { loaded() }

    private suspend fun turnOnParty(rig: HouseRig) = rig.house.turnOn(PARTY, ModeGrant.Person(grant(HousePermissions.STORED_MODE)))

    /** Fails if turning a mode's PIN off without an accepted Admin PIN writes anything. */
    @Test
    fun turningAModesPinOffWithARefusedPinWritesNothing() = runTest {
        val rig = rig()
        rig.withFilmNight(FILM_NIGHT.copy(needsPin = true))
        for (role in listOf(null, Role.ADULT)) {
            val access = TestAccess(role)

            assertThat(actions(access, rig).setNeedsPin(FILM_ID, on = false)).isFalse()

            assertThat(access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))
            assertThat(access.labels).containsExactly(TURN_OFF_NEEDS_A_PIN)
            assertThat(rig.settings.prefs.first().mode(FILM_ID)!!.needsPin).isTrue()
        }
    }

    /** Fails if an Admin's fresh PIN doesn't turn a mode's PIN off, or the pad is shown after the write. */
    @Test
    fun turningAModesPinOffAsksBeforeTheStoreIsTouched() = runTest {
        val rig = rig()
        rig.withFilmNight(FILM_NIGHT.copy(needsPin = true))
        val access = TestAccess(Role.ADMIN)
        val gate = CompletableDeferred<Unit>()
        access.padGate = gate
        var done: Boolean? = null
        launch { done = actions(access, rig).setNeedsPin(FILM_ID, on = false) }
        runCurrent()

        assertThat(access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))
        assertThat(rig.settings.prefs.first().mode(FILM_ID)!!.needsPin).isTrue()
        assertThat(done).isNull()
        gate.complete(Unit)
        runCurrent()

        assertThat(done).isTrue()
        assertThat(rig.settings.prefs.first().mode(FILM_ID)!!.needsPin).isFalse()
    }

    /** Regression guard: turning a mode's PIN on never asks. */
    @Test
    fun turningAModesPinOnNeverAsks() = runTest {
        val rig = rig()
        rig.withFilmNight()
        val access = TestAccess(null)

        assertThat(actions(access, rig).setNeedsPin(FILM_ID, on = true)).isTrue()

        assertThat(access.asked).isEmpty()
        assertThat(rig.settings.prefs.first().mode(FILM_ID)!!.needsPin).isTrue()
    }

    /** Decision 7: fails if a no-PIN mode asks for a PIN, or a Child can't turn it on and off. */
    @Test
    fun aNoPinModeTurnsOnForAChild() = runTest {
        val rig = rig()
        rig.withFilmNight()
        val access = TestAccess(Role.CHILD)
        val actions = actions(access, rig)

        actions.tap(FILM_ID, sawOn = false)

        assertThat(access.asked).isEmpty()
        assertThat(rig.house.onMode.value).isEqualTo(FILM_ID)
        assertThat(rig.plugs.grants.single()).isInstanceOf(ModeGrant.Wall::class.java)
        actions.tap(FILM_ID, sawOn = true)
        assertThat(access.asked).isEmpty()
        assertThat(rig.house.onMode.value).isNull()
    }

    /** Ruling 2: fails if a PIN mode doesn't ask for `party.mode` naming the mode, or a Child's PIN turns it on. */
    @Test
    fun aPinModeAsksAFreshPinNamingTheMode() = runTest {
        val rig = rig()
        rig.withFilmNight(FILM_NIGHT.copy(needsPin = true))
        val adult = TestAccess(Role.ADULT)

        actions(adult, rig).tap(FILM_ID, sawOn = false)

        assertThat(adult.asked).containsExactly(listOf("party.mode"))
        assertThat(adult.labels).containsExactly("Turn Film night on")
        assertThat(rig.house.onMode.value).isEqualTo(FILM_ID)
        actions(adult, rig).tap(FILM_ID, sawOn = true)
        assertThat(adult.labels).containsExactly("Turn Film night on", "Turn Film night off").inOrder()
        assertThat(rig.house.onMode.value).isNull()

        val child = TestAccess(Role.CHILD)
        actions(child, rig).tap(FILM_ID, sawOn = false)
        assertThat(child.asked).hasSize(1)
        assertThat(rig.house.onMode.value).isNull()
    }

    /** Decision 6: fails if a no-PIN mode with a locked plug added turns on without asking. */
    @Test
    fun aLockedPlugMakesTheTapAskForAPin() = runTest {
        val rig = rig()
        rig.withFilmNight()
        rig.plugs.addedNow.value = listOf(LOCKED_HEATER)
        val access = TestAccess(null)

        actions(access, rig).tap(FILM_ID, sawOn = false)

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(rig.house.onMode.value).isNull()
    }

    /** 15b §4.3: fails if a plug locked while the mode is on lets the wall turn it off. */
    @Test
    fun aPlugLockedMidRunMakesTurnOffAsk() = runTest {
        val rig = rig()
        rig.withFilmNight()
        val access = TestAccess(Role.ADULT)
        val actions = actions(access, rig)
        actions.tap(FILM_ID, sawOn = false)
        rig.plugs.addedNow.value = listOf(LOCKED_HEATER)

        actions.tap(FILM_ID, sawOn = true)

        assertThat(access.labels).containsExactly("Turn Film night off")
        assertThat(rig.house.onMode.value).isNull()
        assertThat(rig.trace.calls).contains("plugs.leave $FILM_ID")
    }

    /** S1: fails if a locked plug the run still holds, though taken out of the mode, can be put back with no PIN. */
    @Test
    fun aRemovedLockedPlugStillAsksOnTurnOff() = runTest {
        val rig = rig()
        rig.withFilmNight()
        rig.plugs.addedNow.value = listOf(LOCKED_HEATER.copy(needsPin = false))
        val access = TestAccess(null)
        val actions = actions(access, rig)
        actions.tap(FILM_ID, sawOn = false)
        assertThat(access.asked).isEmpty()
        rig.plugs.addedNow.value = emptyList()
        rig.plugs.holdsPin = true

        actions.tap(FILM_ID, sawOn = true)

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(rig.house.onMode.value).isEqualTo(FILM_ID)
        assertThat(rig.trace.calls).doesNotContain("plugs.leave $FILM_ID")
    }

    /** S2: fails if Holiday mode going on while the PIN pad is up lets the turn-on through, or the tile doesn't say why. */
    @Test
    fun holidayGoingOnWhileThePinIsUpRefuses() = runTest {
        val rig = rig()
        rig.withFilmNight()
        val access = TestAccess(Role.ADULT)
        val gate = CompletableDeferred<Unit>()
        access.padGate = gate
        val actions = actions(access, rig)
        val tap = launch { actions.tap(PARTY, sawOn = false) }
        runCurrent()

        holiday.value = true
        gate.complete(Unit)
        tap.join()

        assertThat(rig.house.isOn.value).isFalse()
        assertThat(rig.trace.calls).isEmpty()
        assertThat(actions.refusal(PARTY).first()).isEqualTo("Turn Holiday mode off first.")
    }

    /** 15b I1: fails if a no-PIN tap turns a mode on while Holiday mode is turning on, or the tile doesn't say why. */
    @Test
    fun holidayTurningOnRefusesANoPinTap() = runTest {
        val rig = rig()
        rig.withFilmNight()
        val access = TestAccess(Role.CHILD)
        val actions = actions(access, rig)
        holidayTurningOn.value = true

        actions.tap(FILM_ID, sawOn = false)

        assertThat(access.asked).isEmpty()
        assertThat(rig.house.isOn.value).isFalse()
        assertThat(actions.refusal(FILM_ID).first()).isEqualTo("Turn Holiday mode off first.")
        holiday.value = true
        holidayTurningOn.value = false
        assertThat(rig.house.isOn.value).isFalse()
        assertThat(actions.refusal(FILM_ID).first()).isEqualTo("Turn Holiday mode off first.")
    }

    /** 15b I1: fails if Holiday mode can turn on while a no-PIN stored mode is turning on, or both end up on. */
    @Test
    fun aStoredModeTurningOnRefusesHoliday() = runTest {
        val rig = rig()
        rig.choose(PARTY_MODE, FILM_NIGHT)
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["plugs.leave"] = gate
        // The first tick's put-back of leftover plugs holds the house's lock, as a slow plug would.
        val tick = launch { rig.house.tick() }
        runCurrent()
        val tap = launch { actions(TestAccess(Role.CHILD), rig).tap(FILM_ID, sawOn = false) }
        runCurrent()
        assertThat(rig.house.isOn.value).isFalse()
        val holidaySwitch = TestSwitch(HouseModes.HOLIDAY, "Holiday mode", holiday)
        val holidayRefusal = ModeRefusal(holidaySwitch, setOf(holidaySwitch, rig.house))

        assertThat(holidayRefusal.turnOnUnlessRefused { holiday.value = true }).isFalse()
        assertThat(holidayRefusal.current()).isEqualTo("Turn Film night off first.")
        gate.complete(Unit)
        tick.join()
        tap.join()

        assertThat(rig.house.onMode.value).isEqualTo(FILM_ID)
        assertThat(holiday.value).isFalse()
        assertThat(holidayRefusal.current()).isEqualTo("Turn Film night off first.")
    }

    /** Fails if a tap made on what the tile showed before the mode went on still acts. */
    @Test
    fun aStaleTapIsIgnored() = runTest {
        val rig = rig()
        rig.withFilmNight()
        turnOnParty(rig)
        val access = TestAccess(Role.ADULT)

        actions(access, rig).tap(PARTY, sawOn = false)

        assertThat(access.asked).isEmpty()
        assertThat(rig.house.onMode.value).isEqualTo(PARTY)
    }

    /** Ruling 14: fails if a plug store that can't be read lets the tap go on with no PIN. */
    @Test
    fun anUnreadablePlugStoreAsksForAPin() = runTest {
        val rig = rig()
        rig.withFilmNight()
        rig.plugs.addedThrows = true
        val access = TestAccess(null)

        actions(access, rig).tap(FILM_ID, sawOn = false)

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(rig.house.onMode.value).isNull()
    }

    /** Decision 1: fails if Holiday mode being on still asks for a PIN, or the line is missing. */
    @Test
    fun holidayOnRefusesBeforeThePin() = runTest {
        val rig = rig()
        rig.withFilmNight()
        val access = TestAccess(Role.ADULT)
        val actions = actions(access, rig)
        holiday.value = true

        actions.tap(PARTY, sawOn = false)

        assertThat(access.asked).isEmpty()
        assertThat(rig.house.isOn.value).isFalse()
        assertThat(actions.refusal(PARTY).first()).isEqualTo("Turn Holiday mode off first.")
        holiday.value = false
        assertThat(actions.refusal(PARTY).first()).isNull()
    }

    /** Decision 1: fails if another stored mode being on asks for a PIN, or the line shows on any tile but the tapped one. */
    @Test
    fun anotherStoredModeRefusesBeforeThePin() = runTest {
        val rig = rig()
        rig.withFilmNight()
        turnOnParty(rig)
        val access = TestAccess(Role.ADULT)
        val actions = actions(access, rig)

        actions.tap(FILM_ID, sawOn = false)

        assertThat(access.asked).isEmpty()
        assertThat(rig.house.onMode.value).isEqualTo(PARTY)
        assertThat(actions.refusal(FILM_ID).first()).isEqualTo("Turn Party mode off first.")
        assertThat(actions.refusal(PARTY).first()).isNull()
        rig.house.turnOff(ModeGrant.Person(grant(HousePermissions.STORED_MODE)))
        assertThat(actions.refusal(FILM_ID).first()).isNull()
    }

    /** Fails if turning a mode off is refused because another mode is on. */
    @Test
    fun turningOffNeverRefuses() = runTest {
        val rig = rig()
        rig.withFilmNight()
        turnOnParty(rig)
        holiday.value = true
        val access = TestAccess(Role.ADULT)
        val actions = actions(access, rig)

        actions.tap(PARTY, sawOn = true)

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(rig.house.isOn.value).isFalse()
        assertThat(actions.refusal(PARTY).first()).isNull()
    }

    /** Fails if a tap while a change runs asks for a PIN or acts. */
    @Test
    fun aTapWhileRunningDoesNothing() = runTest {
        val rig = rig()
        rig.withFilmNight(FILM_NIGHT.copy(needsPin = true))
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["plugs.begin"] = gate
        val turning = launch { turnOnParty(rig) }
        runCurrent()
        assertThat(rig.house.running.value).isEqualTo(ModeRunning(PARTY, ModeRun.TURNING_ON))
        val access = TestAccess(Role.ADULT)

        actions(access, rig).tap(FILM_ID, sawOn = false)

        assertThat(access.asked).isEmpty()
        gate.complete(Unit)
        turning.join()
        assertThat(rig.house.onMode.value).isEqualTo(PARTY)
    }

    @Test
    fun aTapBeforeTheRecordIsReadDoesNothing() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)

        actions(access, rig).tap(PARTY, sawOn = false)

        assertThat(access.asked).isEmpty()
        assertThat(rig.trace.calls).isEmpty()
    }

    /** Fails if the tile leaving Home after the PIN cuts the turn-on short. */
    @Test
    fun theRunOutlivesTheTile() = runTest {
        val rig = rig()
        rig.withFilmNight(FILM_NIGHT.copy(needsPin = true))
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["music.start"] = gate
        val tap = launch { actions(TestAccess(Role.ADULT), rig).tap(FILM_ID, sawOn = false) }
        runCurrent()
        assertThat(rig.house.running.value).isEqualTo(ModeRunning(FILM_ID, ModeRun.TURNING_ON))

        tap.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat(rig.house.onMode.value).isEqualTo(FILM_ID)
        assertThat(rig.trace.calls).contains("lights.modeOn")
        assertThat(rig.house.running.value).isNull()
    }

    private companion object {
        const val PARTY = HouseModes.PARTY
    }
}
