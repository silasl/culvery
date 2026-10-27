package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import uk.co.siland.culvery.capability.plugs.PlugCandidates
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

/** 12 §4.3: the Plugs tab: its header, its cards, and Add, settings and Edit behind a PIN. */
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
                    onSwitch = { switched += it.name }, onSettings = {}, onAdd = {}, onEdit = {}, onDone = {}, onChoice = { _, _, _ -> },
                )
            }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /** Fails if the empty tab doesn't say how to add a plug, offers no Add, or draws a mode column. */
    @Test
    fun noPlugsSaysHowToAdd() {
        show(plugs = emptyList())
        compose.onNodeWithTag("plugs_empty").assertTextEquals("No plugs yet. Add one from Home Assistant.")
        compose.onNodeWithTag("plugs_add").assertTextEquals("Add a plug")
        compose.onAllNodesWithTag("mode_holiday").assertCountEquals(0)
        compose.onAllNodesWithTag("plugs_edit").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the header line isn't "{on} of {n} on · Home Assistant". */
    @Test
    fun theHeaderCountsWhatsOn() {
        show(plugs = listOf(LAMP_ON, FAN_OFF, FAIRY_LOST, PlugState(DESK, "Desk", PlugPower.ON, null)))
        compose.onNodeWithText("2 of 4 on · Home Assistant").assertIsDisplayed()
        compose.onNodeWithText("Plugs").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
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

    /** Fails if the sections aren't drawn for every mode, or each plug has no card. */
    @Test
    fun aCardPerPlugAndASectionPerMode() {
        show(plugs = listOf(LAMP_ON, FAN_OFF, DESK_OFF), modes = listOf(PARTY_MODE, HOLIDAY_MODE))
        assertThat(listOf(count("plug_card_0"), count("plug_card_1"), count("plug_card_2"), count("plug_card_3"))).containsExactly(1, 1, 1, 0).inOrder()
        assertThat(listOf(count("mode_holiday"), count("mode_party"))).containsExactly(1, 1)
    }

    /** Fails if a tap on a plain plug's button doesn't hand that plug on once. */
    @Test
    fun aPlainPlugSwitchesOnTap() {
        show()
        compose.onNodeWithTag("plug_card_1_switch").performClick()
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
            runBlocking { rig.configure { prefs -> states.fold(prefs) { acc, it -> acc.adding(it.entityId) } } }
            rig.system.found = PlugCandidates.Found(CANDIDATES)
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

        fun chips() = count("mode_holiday_0_on") > 0

        fun edit() {
            compose.onNodeWithTag("plugs_edit").performClick()
            idle()
        }
    }

    /** Fails if Add a plug opens its sheet without the manage grant being asked for, or a Child gets it. */
    @Test
    fun addAsksBeforeTheSheet() {
        val host = Host(Role.ADULT, LAMP_ON)
        compose.onNodeWithTag("plugs_add").performClick()
        compose.waitFor { count("add_plug_sheet") > 0 }
        assertThat(host.access.asked).containsExactly(listOf("plugs.manage"))
        compose.waitFor { count("add_plug_0") > 0 }
        compose.onNodeWithTag("add_plug_0").assertTextEquals("Fan")
    }

    /** Fails if a Child's refused PIN still opens Add a plug. */
    @Test
    fun aChildGetsNoAddSheet() {
        val host = Host(Role.CHILD, LAMP_ON)
        compose.onNodeWithTag("plugs_add").performClick()
        host.idle()
        assertThat(host.access.asked).containsExactly(listOf("plugs.manage"))
        assertThat(count("add_plug_sheet")).isEqualTo(0)
    }

    /** Fails if picking a switch doesn't add it to the tab and close the sheet. */
    @Test
    fun pickingAPlugAddsItAndClosesTheSheet() {
        val host = Host(Role.ADULT, LAMP_ON)
        compose.onNodeWithTag("plugs_add").performClick()
        compose.waitFor { count("add_plug_0") > 0 }
        compose.onNodeWithTag("add_plug_0").performClick()
        compose.waitFor { count("add_plug_sheet") == 0 }
        compose.waitFor { host.prefs().plugs == listOf(LAMP, FAN) }
    }

    /** Fails if the › opens the plug's settings without the manage grant, or a Child gets them. */
    @Test
    fun settingsAskBeforeTheSheet() {
        val host = Host(Role.ADULT, LAMP_ON)
        compose.onNodeWithTag("plug_card_0_settings").performClick()
        compose.waitFor { count("plug_settings_sheet") > 0 }
        assertThat(host.access.asked).containsExactly(listOf("plugs.manage"))
        compose.onNodeWithTag("plug_settings_sheet").assertIsDisplayed()
    }

    /** Fails if a Child's refused PIN still opens a plug's settings. */
    @Test
    fun aChildGetsNoSettings() {
        val host = Host(Role.CHILD, LAMP_ON)
        compose.onNodeWithTag("plug_card_0_settings").performClick()
        host.idle()
        assertThat(host.access.asked).containsExactly(listOf("plugs.manage"))
        assertThat(count("plug_settings_sheet")).isEqualTo(0)
    }

    /** Fails if Remove in the settings doesn't take the plug off the tab and close the sheet. */
    @Test
    fun removingFromTheSettingsTakesItOffTheTab() {
        val host = Host(Role.ADULT, LAMP_ON)
        compose.onNodeWithTag("plug_card_0_settings").performClick()
        compose.waitFor { count("plug_settings_remove") > 0 }
        compose.onNodeWithTag("plug_settings_remove").performClick()
        compose.waitFor { count("plug_settings_sheet") == 0 }
        compose.waitFor { host.prefs().plugs.isEmpty() }
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
        compose.onNodeWithTag("mode_holiday_0_off").performClick()
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
        compose.onNodeWithTag("mode_holiday_0_on").performClick()
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
