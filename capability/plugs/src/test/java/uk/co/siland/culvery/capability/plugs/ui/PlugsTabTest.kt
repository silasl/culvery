package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.DESK
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.MODES
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugsRig
import uk.co.siland.culvery.capability.plugs.TestAccess
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 12 §4.3: the Plugs tab: its header, its rows, and Edit behind a PIN. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsTabTest {
    @get:Rule val compose = createComposeRule()
    private val switched = mutableListOf<String>()

    private fun show(
        plugs: List<PlugState> = listOf(LAMP_ON, FAN_OFF),
        problem: String? = null,
        modes: List<HouseModeInfo> = listOf(HOLIDAY_MODE),
        prefs: PlugPrefs = PlugPrefs(),
        editing: Boolean = false,
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(1116.dp, 690.dp)) {
                PlugsTab(
                    plugs, prefs.needsPin, emptySet(), problem, modes, prefs, editing,
                    onSwitch = { switched += it.name }, onEdit = {}, onDone = {}, onChoice = { _, _, _ -> },
                )
            }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /** Fails if the header isn't "{on} of {n} on · Home Assistant" over plugs known to be On or Off, or isn't just "Home Assistant" with none known. */
    @Test
    fun theHeaderCountsKnownPlugsOnly() {
        show(plugs = listOf(LAMP_ON, FAN_OFF, FAIRY_LOST, PlugState(DESK, "Desk", PlugPower.ON, null), PlugState(DESK + "2", "Hall", PlugPower.UNKNOWN, null)))
        compose.onNodeWithText("2 of 3 on · Home Assistant").assertIsDisplayed()
        compose.onNodeWithText("Plugs").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tab with no plug's state known says anything but "Home Assistant". */
    @Test
    fun noStateKnownSaysJustHomeAssistant() {
        show(plugs = listOf(FAIRY_LOST, PlugState(DESK, "Desk", PlugPower.UNKNOWN, null)))
        compose.onNodeWithText("Home Assistant").assertIsDisplayed()
        compose.onAllNodesWithText("of", substring = true).assertCountEquals(0)
    }

    /** Fails if a link problem doesn't show under the header. */
    @Test
    fun aProblemShowsUnderTheHeader() {
        show(problem = "Home Assistant can’t be reached.")
        compose.onNodeWithTag("plugs_line").assertTextEquals("Home Assistant can’t be reached.")
        assertThat(compose.onNodeWithTag("plugs_line").fetchSemanticsNode().boundsInRoot.top)
            .isAtLeast(compose.onNodeWithText("Plugs").fetchSemanticsNode().boundsInRoot.bottom)
    }

    /** Fails if no problem still draws a line. */
    @Test
    fun noProblemNoLine() {
        show()
        compose.onAllNodesWithTag("plugs_line").assertCountEquals(0)
    }

    /** Fails if the rows aren't in the order given, each mode has no column heading, or a heading isn't over its cells. */
    @Test
    fun aRowPerPlugInOrderAndAHeadingPerMode() {
        show(plugs = listOf(FAN_OFF, LAMP_ON, DESK_OFF), modes = listOf(PARTY_MODE, HOLIDAY_MODE))
        val tops = listOf(FAN, LAMP, DESK).map { compose.onNodeWithTag("plug_$it").fetchSemanticsNode().boundsInRoot.top }
        assertThat(tops).isInOrder()
        assertThat(count("plug_$FAN") + count("plug_$LAMP") + count("plug_$DESK")).isEqualTo(3)
        assertThat(listOf(count("mode_heading_holiday"), count("mode_heading_party"))).containsExactly(1, 1)
        for (mode in listOf("holiday", "party")) {
            val heading = compose.onNodeWithTag("mode_heading_$mode").fetchSemanticsNode().boundsInRoot
            val cell = compose.onNodeWithTag("mode_${mode}_$FAN", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertThat(cell.center.x).isWithin(1f).of(heading.center.x)
            assertThat(heading.bottom).isAtMost(compose.onNodeWithTag("plug_$FAN").fetchSemanticsNode().boundsInRoot.top)
        }
        assertThat(compose.onNodeWithTag("mode_heading_holiday").fetchSemanticsNode().boundsInRoot.left)
            .isLessThan(compose.onNodeWithTag("mode_heading_party").fetchSemanticsNode().boundsInRoot.left)
    }

    /** Fails if a tab with no plug at all draws an empty state, an Add button or a mode heading. */
    @Test
    fun noPlugsDrawsNoRows() {
        show(plugs = emptyList())
        compose.onAllNodesWithTag("plug_$LAMP").assertCountEquals(0)
        compose.onNodeWithText("Add a plug").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tap on a plain plug's button doesn't hand that plug on once. */
    @Test
    fun aPlainPlugSwitchesOnTap() {
        show()
        compose.onNodeWithTag("plug_${FAN}_switch").performClick()
        assertThat(switched).containsExactly("Fan")
    }

    /** What [PlugsTabHost] takes, over a fake system holding [states]. */
    private inner class Host(role: Role?, vararg states: PlugState) {
        val rig = PlugsRig(*states)
        val access = TestAccess(role)
        val actions = PlugActions(access, rig.settings, rig.system, RecordingToaster(), CoroutineScope(Job()))
        val overlay = TestOverlay()
        var tabShown by mutableStateOf(true)

        init {
            runBlocking { rig.configure { prefs -> states.fold(prefs) { acc, it -> acc.showing(it.entityId) } } }
            compose.setContent {
                CulveryTheme(dark = true) {
                    CompositionLocalProvider(LocalOverlayHost provides overlay) {
                        Box(Modifier.size(1116.dp, 690.dp)) {
                            if (tabShown) PlugsTabHost(rig.system, actions, rig.settings, MODES)
                            overlay.content?.invoke()
                        }
                    }
                }
            }
        }

        fun idle() = compose.waitForIdle()

        fun prefs() = runBlocking { rig.prefs() }

        fun chips() = count("mode_holiday_${LAMP}_on") > 0

        fun edit() {
            compose.onNodeWithTag("plugs_edit").performClick()
            idle()
        }
    }

    private fun switchButton(id: String = FAN) = compose.onNodeWithTag("plug_${id}_switch")

    /** Fails if the PIN plug says Switching… while the pad is up, doesn't once the PIN is in, or a tap during it reaches the system again. */
    @Test
    fun switchingShowsOnlyAfterThePin() {
        val host = Host(Role.ADULT, FAN_OFF)
        runBlocking { host.rig.configure { it.copy(needsPin = setOf(FAN)) } }
        val pad = CompletableDeferred<Unit>().also { host.access.padGate = it }
        val gate = CompletableDeferred<Unit>().also { host.rig.system.switchGates[FAN] = it }
        compose.waitFor { count("plug_${FAN}_switch") > 0 }
        switchButton().assertContentDescriptionEquals("Switch on, needs a PIN").performClick()
        compose.waitFor { host.access.asked.size == 1 }
        host.idle()
        switchButton().assertContentDescriptionEquals("Switch on, needs a PIN")

        pad.complete(Unit)
        compose.waitFor { host.rig.system.switches.size == 1 }
        compose.waitFor { count("plug_${FAN}_switch") > 0 && switchButton().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == "Switching…" }
        gate.complete(Unit)
        compose.waitFor { switchButton().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() != "Switching…" }
        assertThat(host.rig.system.switches).containsExactly(FAN to true)
    }

    /** Fails if a Child's PIN switches a plug that needs one. */
    @Test
    fun aChildPinIsRefused() {
        val host = Host(Role.CHILD, FAN_OFF)
        runBlocking { host.rig.configure { it.copy(needsPin = setOf(FAN)) } }
        compose.waitFor { count("plug_${FAN}_switch") > 0 }
        switchButton().performClick()
        compose.waitFor { host.access.asked.size == 1 }
        host.idle()
        assertThat(host.rig.system.switches).isEmpty()
        switchButton().assertContentDescriptionEquals("Switch on, needs a PIN")
    }

    /** Fails if Edit shows its chips without the manage grant being asked for, or a Child gets them. */
    @Test
    fun editAsksForAPin() {
        val host = Host(Role.ADULT, LAMP_ON)
        assertThat(host.chips()).isFalse()
        host.edit()
        assertThat(host.access.asked).containsExactly(listOf("plugs.manage"))
        assertThat(host.chips()).isTrue()
        compose.onNodeWithTag("plugs_edit").assertTextEquals("Done")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a Child's refused PIN still turns Edit on. */
    @Test
    fun aChildGetsNoChips() {
        val host = Host(Role.CHILD, LAMP_ON)
        host.edit()
        assertThat(host.access.asked).containsExactly(listOf("plugs.manage"))
        assertThat(host.chips()).isFalse()
        compose.onNodeWithTag("plugs_edit").assertTextEquals("Edit")
    }

    /** Fails if a chip tap doesn't save that mode's choice for that plug. */
    @Test
    fun aChipTapIsSaved() {
        val host = Host(Role.ADULT, LAMP_ON)
        host.edit()
        compose.onNodeWithTag("mode_holiday_${LAMP}_off").performClick()
        compose.waitFor { host.prefs().choice("holiday", LAMP) == ModeChoice.OFF }
    }

    /** Edit's timer starts when the chips show: one tick before 2 minutes they stay, at 2 minutes they go. */
    @Test
    fun editEndsOnDoneOrAfterTwoIdleMinutes() {
        val host = Host(Role.ADULT, LAMP_ON)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("plugs_edit").performClick()
        repeat(20) { if (!host.chips()) compose.mainClock.advanceTimeByFrame() }
        assertThat(host.chips()).isTrue()
        compose.mainClock.advanceTimeBy(EDIT_IDLE_MS - 1, ignoreFrameDuration = true)
        assertThat(host.chips()).isTrue()
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        compose.mainClock.advanceTimeByFrame()
        assertThat(host.chips()).isFalse()
        compose.mainClock.autoAdvance = true

        host.edit()
        assertThat(host.chips()).isTrue()
        host.edit()
        assertThat(host.chips()).isFalse()
    }

    /** Fails if a change doesn't start the 2 minutes again. */
    @Test
    fun aChangeRestartsTheTimer() {
        val host = Host(Role.ADULT, LAMP_ON)
        host.edit()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(EDIT_IDLE_MS - 1_000, ignoreFrameDuration = true)
        compose.onNodeWithTag("mode_holiday_${LAMP}_on").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(EDIT_IDLE_MS - 1_000, ignoreFrameDuration = true)
        assertThat(host.chips()).isTrue()
        compose.mainClock.advanceTimeBy(1_000, ignoreFrameDuration = true)
        compose.mainClock.advanceTimeByFrame()
        assertThat(host.chips()).isFalse()
        compose.mainClock.autoAdvance = true
    }

    /** Fails if Edit is still on when the tab is shown again. */
    @Test
    fun editEndsOnLeavingTheTab() {
        val host = Host(Role.ADULT, LAMP_ON)
        host.edit()
        assertThat(host.chips()).isTrue()
        host.tabShown = false
        host.idle()
        host.tabShown = true
        host.idle()
        assertThat(host.chips()).isFalse()
    }

    /** Fails if the system is watched while the tab isn't composed, or not while it is. */
    @Test
    fun theTabWatchesWhileShown() {
        val host = Host(Role.ADULT, LAMP_ON)
        host.idle()
        assertThat(host.rig.system.watchers).isEqualTo(1)
        host.tabShown = false
        host.idle()
        assertThat(host.rig.system.watchers).isEqualTo(0)
    }

    /** Fails if the system's own problem line isn't shown under the header. */
    @Test
    fun theHostShowsTheSystemsProblem() {
        val host = Host(Role.ADULT, LAMP_ON)
        host.rig.system.problem.value = "Home Assistant can’t be reached."
        compose.waitFor { count("plugs_line") > 0 }
        compose.onNodeWithTag("plugs_line").assertTextEquals("Home Assistant can’t be reached.")
    }
}
