package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.AWAY
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.LightsConnect
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightsPrefs
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.capability.lights.LightsSettings
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.MIMIC
import uk.co.siland.culvery.capability.lights.TestLightsProvider
import uk.co.siland.culvery.capability.lights.link
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.core.ui.test.waitFor

private val SEEN = listOf(room("Lounge"), room("Kitchen"), room("Garden"))
private val FOUND = BridgeFound("001788fffe00a1b2", "192.168.1.30")

/** 7 §4.1, §5.3: Hue Bridge, the main room, the rooms All lights off leaves out and Holiday mode's automations. */
@RunWith(AndroidJUnit4::class)
class LightsSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val lights = LightsFixture()
    private val calls = mutableListOf<String>()
    private val paired = LightsSettingsUi(
        paired = true, status = null, findLine = null, finding = false, rooms = SEEN, mainRoomId = null,
        leftOut = emptySet(), goneLeftOut = 0, automations = listOf(MIMIC, AWAY), holidayAutomations = emptySet(),
    )

    private fun show(ui: LightsSettingsUi) = compose.setContent {
        CulveryTheme(dark = true) {
            InSettingsPane {
                LightsSettingsPage(
                    ui,
                    onFind = { calls += "find" },
                    onEnterAddress = { calls += "address" },
                    onMainRoom = { calls += "main $it" },
                    onLeftOut = { calls += "left out ${it.sorted()}" },
                    onHolidayAutomations = { calls += "holiday ${it.sorted()}" },
                )
            }
        }
    }

    private fun checkbox() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    /** Fails if an unpaired page doesn't say so, or Find Hue Bridge and the address button don't call out. */
    @Test
    fun unpairedOffersFindAndAddress() {
        show(paired.copy(paired = false, rooms = emptyList(), automations = null))
        compose.onNodeWithTag("lights_connection").assertTextEquals("Not paired yet. Find Hue Bridge, or enter the bridge’s address.")
        compose.onAllNodesWithTag("lights_main_Lounge-id").assertCountEquals(0)
        compose.onNodeWithTag("lights_find").performClick()
        compose.onNodeWithTag("lights_address").assertTextEquals("Enter the bridge’s address").performClick()
        assertThat(calls).containsExactly("find", "address").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a Find in progress doesn't say it is looking, or can be tapped again. */
    @Test
    fun findingShowsLookingThenThePairingSheet() {
        show(paired.copy(finding = true))
        compose.onNodeWithTag("lights_find").assertTextEquals("Looking for the Hue Bridge…").assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a find that found nothing doesn't show why. */
    @Test
    fun aFindThatFoundNothingSaysWhy() {
        show(paired.copy(paired = false, findLine = "No Hue Bridge answered. Check it’s plugged in, or enter the bridge’s address."))
        compose.onNodeWithTag("lights_find_line").assertTextEquals("No Hue Bridge answered. Check it’s plugged in, or enter the bridge’s address.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the sheet doesn't tell the user to press the button, or Try again doesn't call out. */
    @Test
    fun notPressedInTimeOffersTryAgain() {
        var again = 0
        compose.setContent { CulveryTheme(dark = true) { PairingSheet(PairingState.NOT_IN_TIME, onTryAgain = { again++ }, onClose = {}) } }
        compose.onNodeWithText("Press the round button on top of the Hue Bridge").assertExists()
        compose.onNodeWithTag("lights_pairing_line").assertTextEquals("The button wasn’t pressed in time. Try again.")
        compose.onNodeWithTag("lights_pairing_again").assertTextEquals("Try again").performClick()
        assertThat(again).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the waiting sheet offers Try again, or doesn't say it is waiting. */
    @Test
    fun waitingForTheButtonOffersNoRetry() {
        compose.setContent { CulveryTheme(dark = true) { PairingSheet(PairingState.WAITING, onTryAgain = {}, onClose = {}) } }
        compose.onNodeWithTag("lights_pairing_line").assertTextEquals("Waiting for the button…")
        compose.onAllNodesWithTag("lights_pairing_again").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the sheet doesn't say where the address is shown, send what was typed, or show the line it gets back. */
    @Test
    fun theAddressSheetSaysHowToTypeIt() {
        val sent = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = true) {
                AddressSheet(onConnect = {
                    sent += it
                    "Type the address as four numbers, like 192.168.1.30."
                }, onEdited = {}, onClose = {})
            }
        }
        compose.onNodeWithText("Shown in the Hue app under Settings › My Hue system, on the bridge.").assertExists()
        compose.onNodeWithTag("lights_address_pair").assertIsNotEnabled()
        compose.onNodeWithTag("lights_address_field").performTextInput("192.168.1.30:80")
        compose.onNodeWithTag("lights_address_pair").assertTextEquals("Pair with this bridge").performClick()
        compose.waitFor { sent.isNotEmpty() }
        compose.onNodeWithTag("lights_address_line").assertTextEquals("Type the address as four numbers, like 192.168.1.30.")
        assertThat(sent).containsExactly("192.168.1.30:80")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A failed connect isn't the text's fault: its line shows once and the field keeps its own edge. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aFailedConnectShowsItsLineOnceAndDoesNotRingTheField() {
        compose.setContent { CulveryTheme(dark = true) { AddressSheet(onConnect = { "Type the address as four numbers, like 192.168.1.30." }, onEdited = {}, onClose = {}) } }
        compose.onNodeWithTag("lights_address_field").performTextInput("192.168.1.30:80")
        compose.onNodeWithTag("lights_address_pair").performClick()
        compose.waitFor { compose.onAllNodesWithText("Type the address as four numbers, like 192.168.1.30.").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Type the address as four numbers, like 192.168.1.30.").assertCountEquals(1)
        compose.onNodeWithTag("lights_address_line").assertTextEquals("Type the address as four numbers, like 192.168.1.30.")
        assertThat(pixelAt(compose.onNodeWithTag("lights_address_field"), 1.dp, 30.dp)).isNotEqualTo(DarkColors.danger.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the picker doesn't mark Hue's first room when none is chosen, or a tap doesn't choose the room. */
    @Test
    fun theMainRoomPicker() {
        show(paired)
        compose.onNodeWithTag("lights_main_Lounge-id").assertIsSelected()
        compose.onNodeWithTag("lights_main_Garden-id").assertIsNotSelected().performClick()
        assertThat(calls).containsExactly("main Garden-id")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the chosen room isn't the selected one. */
    @Test
    fun theChosenMainRoomIsSelected() {
        show(paired.copy(mainRoomId = "Kitchen-id"))
        compose.onNodeWithTag("lights_main_Kitchen-id").assertIsSelected()
        compose.onNodeWithTag("lights_main_Lounge-id").assertIsNotSelected()
    }

    /** Fails if rooms start ticked, ticking a room doesn't save it, or a tick isn't a checkbox. */
    @Test
    fun leftOutTicks() {
        show(paired)
        SEEN.forEach { compose.onNodeWithTag("lights_left_out_${it.id}").assertIsNotSelected() }
        compose.onNodeWithTag("lights_left_out_Garden-id").assert(checkbox()).performClick()
        assertThat(calls).containsExactly("left out [Garden-id]")
        compose.onAllNodesWithTag("lights_gone_line").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a second tap on a ticked room doesn't untick it, or loses the other ticks. */
    @Test
    fun tickingATickedRoomUnticksIt() {
        show(paired.copy(leftOut = setOf("Garden-id", "Kitchen-id")))
        compose.onNodeWithTag("lights_left_out_Garden-id").assertIsSelected().performClick()
        assertThat(calls).containsExactly("left out [Kitchen-id]")
    }

    /** Fails if a room the bridge dropped shows a chip, the line is missing, or the next save keeps its id. */
    @Test
    fun leftOutTicksDropAGoneRoom() {
        show(paired.copy(rooms = listOf(room("Kitchen"), room("Garden")), leftOut = setOf("Garden-id", "Gone-id"), goneLeftOut = 1))
        compose.onAllNodesWithTag("lights_left_out_Gone-id").assertCountEquals(0)
        compose.onNodeWithTag("lights_gone_line").assertTextEquals("1 room left out is no longer on the bridge.")
        compose.onNodeWithTag("lights_left_out_Kitchen-id").performClick()
        assertThat(calls).containsExactly("left out [Garden-id, Kitchen-id]")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the line doesn't pluralise. */
    @Test
    fun severalGoneRoomsSaySoInThePlural() {
        show(paired.copy(goneLeftOut = 2))
        compose.onNodeWithTag("lights_gone_line").assertTextEquals("2 rooms left out are no longer on the bridge.")
    }

    /** Fails if the note about Holiday mode is missing under an empty tick, or ticking doesn't save. */
    @Test
    fun holidayAutomationsTicks() {
        show(paired)
        compose.onNodeWithTag("lights_holiday_note").assertTextEquals("Holiday mode turns all lights off and shows On.")
        compose.onNodeWithTag("lights_automation_mimic-id").assert(checkbox()).performClick()
        assertThat(calls).containsExactly("holiday [mimic-id]")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the note stays once an automation is ticked. */
    @Test
    fun theNoteGoesOnceOneIsTicked() {
        show(paired.copy(holidayAutomations = setOf("mimic-id")))
        compose.onNodeWithTag("lights_automation_mimic-id").assertIsSelected()
        compose.onAllNodesWithTag("lights_holiday_note").assertCountEquals(0)
    }

    /** Fails if a bridge with no automations doesn't say how to get one. */
    @Test
    fun noAutomationsYet() {
        show(paired.copy(automations = emptyList()))
        compose.onNodeWithTag("lights_no_automations").assertTextEquals("No Hue automations yet. Set one up in the Hue app, such as Mimic presence.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a ticked automation the bridge dropped shows, or the next save keeps its id. */
    @Test
    fun aGoneAutomationIsNotListed() {
        show(paired.copy(holidayAutomations = setOf("mimic-id", "gone-id")))
        compose.onAllNodesWithTag("lights_automation_gone-id").assertCountEquals(0)
        compose.onNodeWithTag("lights_automation_away-id").performClick()
        assertThat(calls).containsExactly("holiday [away-id, mimic-id]")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a problem doesn't take the connection line's place with the same words as the card. */
    @Test
    fun theStatusIsTheProblemLine() {
        val line = problemLine(LightsProblem.UNPAIRED)
        show(paired.copy(status = line))
        compose.onNodeWithTag("lights_status").assertTextEquals(line)
        compose.onAllNodesWithTag("lights_connection").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private inner class Host(store: DataStore<Preferences>? = null, pairFails: Boolean = false) {
        val settings = store?.let { LightsSettings(it) } ?: lights.settings

        /** The saved choices as they come, for a check that can't wait on the flow. */
        val saved = settings.prefs.stateIn(lights.scope, SharingStarted.Eagerly, LightsPrefs())
        val hue = TestLightsProvider()
        val bridges = Bridges(setOf(hue), settings, lights.scope)
        val view = LightsView(bridges, settings, lights.scope)
        val toaster = RecordingToaster()
        val overlay = TestOverlay()
        val connect = LightsConnect(if (pairFails) unreachableOnPair(hue) else hue) { id, link -> settings.connect(id, link) }

        fun open() {
            runBlocking { settings.connect("lights.hue", link()) }
            adopt()
        }

        fun adopt() {
            compose.waitFor { hue.systems.isNotEmpty() }
            hue.systems.single().rooms.value = SEEN
        }
    }

    private fun unreachableOnPair(inner: LightsProvider): LightsProvider = object : LightsProvider by inner {
        override suspend fun pair(bridge: BridgeFound) = throw LightsUnreachableException(LightsProblem.UNREACHABLE)
    }

    private fun showHost(h: Host) {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides h.overlay) {
                    InSettingsPane { LightsSettingsHost(h.settings, h.bridges, h.view, h.connect, h.toaster) }
                    h.overlay.content?.invoke()
                }
            }
        }
        compose.waitFor { compose.onAllNodesWithTag("lights_find").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun appeared(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Fails if the pairing goes on after the shell closed its sheet, or then closes whatever sheet is showing. */
    @Test
    fun theShellClosingThePairingSheetEndsThePairing() {
        val h = Host().apply { hue.found = FOUND }
        showHost(h)
        compose.onNodeWithTag("lights_find").performClick()
        compose.waitFor { appeared("lights_pairing_sheet") }
        h.overlay.closedByHost()
        h.overlay.show { androidx.compose.material3.Text("Another sheet", modifier = androidx.compose.ui.Modifier.testTag("other_sheet")) }
        h.hue.pairAnswers += link()
        compose.waitForIdle()
        // a run still going asks again within 2 s and would save the link
        runCatching { compose.waitUntil(3_000) { h.toaster.shown.isNotEmpty() } }
        assertThat(h.toaster.shown).isEmpty()
        assertThat(h.hue.pairAnswers).hasSize(1)
        assertThat(appeared("other_sheet")).isTrue()
    }

    /** Fails if what was typed in the address field leaves the pairing sheet marked as having unsaved changes. */
    @Test
    fun thePairingSheetStartsWithNothingToDiscard() {
        val h = Host().apply { hue.bridges["192.168.1.30"] = FOUND }
        showHost(h)
        compose.onNodeWithTag("lights_address").performClick()
        compose.waitFor { appeared("lights_address_field") }
        compose.onNodeWithTag("lights_address_field").performTextInput("192.168.1.30")
        assertThat(h.overlay.edited).isTrue()
        compose.onNodeWithTag("lights_address_pair").performClick()
        compose.waitFor { appeared("lights_pairing_sheet") }
        assertThat(h.overlay.edited).isFalse()
    }

    /** Fails if pairing doesn't close the sheet, save the link, say so, or show Paired. */
    @Test
    fun pairedClosesTheSheetAndSaysSo() {
        val h = Host().apply { hue.found = FOUND; hue.pairAnswers += link() }
        showHost(h)
        compose.onNodeWithTag("lights_find").performClick()
        compose.waitFor { h.toaster.shown.isNotEmpty() }
        assertThat(h.toaster.shown).containsExactly("Paired with the Hue Bridge.")
        assertThat(h.overlay.content).isNull()
        assertThat(h.hue.asked.take(2)).containsExactly("find", "pair").inOrder()
        compose.waitFor { appeared("lights_connection") }
        compose.onNodeWithTag("lights_connection").assertTextEquals("Paired.")
    }

    /** Fails if the pairing sheet can be closed by the idle timer while it waits, or lacks the words. */
    @Test
    fun theWaitingSheetDoesNotCloseWhenIdle() {
        val h = Host().apply { hue.found = FOUND }
        showHost(h)
        compose.onNodeWithTag("lights_find").performClick()
        compose.waitFor { appeared("lights_pairing_sheet") }
        assertThat(h.overlay.closesWhenIdle).isFalse()
        compose.onNodeWithText("Press the round button on top of the Hue Bridge").assertExists()
        compose.onNodeWithTag("lights_pairing_line").assertTextEquals("Waiting for the button…")
    }

    /** Fails if a bridge that can't be reached while pairing leaves the sheet up or hides why. */
    @Test
    fun aBridgeThatCantBeReachedWhilePairingClosesTheSheetWithItsLine() {
        val h = Host(pairFails = true).apply { hue.found = FOUND }
        showHost(h)
        compose.onNodeWithTag("lights_find").performClick()
        compose.waitFor { h.overlay.content == null && appeared("lights_find_line") }
        compose.waitFor { runCatching { compose.onNodeWithTag("lights_find_line").assertTextEquals(problemLine(LightsProblem.UNREACHABLE)) }.isSuccess }
    }

    /** Fails if the host doesn't read the rooms and the automations once as the page opens, or doesn't list them. */
    @Test
    fun theHostReadsOnceAndListsWhatTheBridgeHas() {
        val h = Host()
        showHost(h)
        h.open()
        compose.waitFor { appeared("lights_main_Lounge-id") && appeared("lights_automation_mimic-id") }
        assertThat(h.hue.systems.single().calls.filter { it == "readAll" || it == "automations" }).containsExactly("readAll", "automations").inOrder()
    }

    /** Fails if a tick doesn't save at once, or a gone room's id survives the next save. */
    @Test
    fun ticksSaveAtOnceAndDropAGoneRoom() {
        val h = Host()
        showHost(h)
        h.open()
        runBlocking { h.settings.setLeftOut(setOf("Garden-id", "Gone-id")) }
        compose.waitFor { appeared("lights_gone_line") }
        compose.onNodeWithTag("lights_gone_line").assertTextEquals("1 room left out is no longer on the bridge.")
        compose.onNodeWithTag("lights_left_out_Kitchen-id").performClick()
        compose.waitFor { h.saved.value.leftOut == setOf("Garden-id", "Kitchen-id") }
        compose.onNodeWithTag("lights_main_Garden-id").performClick()
        compose.waitFor { h.saved.value.mainRoomId == "Garden-id" }
        compose.waitFor { appeared("lights_automation_away-id") }
        compose.onNodeWithTag("lights_automation_away-id").performClick()
        compose.waitFor { h.saved.value.holidayAutomations == setOf("away-id") }
    }

    /** Fails if a choice that couldn't be saved says nothing, or says anything but the standard line. */
    @Test
    fun aFailedSaveSaysSo() {
        val saved = mapOf("provider" to "lights.hue", "bridge" to FOUND.bridgeId, "address" to FOUND.address, "key" to "test-key")
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(mutablePreferencesOf().apply { saved.forEach { (k, v) -> this[stringPreferencesKey(k)] = v } })

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk full")
        }
        val h = Host(failing)
        showHost(h)
        h.adopt()
        compose.waitFor { appeared("lights_left_out_Garden-id") }
        compose.onNodeWithTag("lights_left_out_Garden-id").performClick()
        compose.waitFor { h.toaster.shown.isNotEmpty() }
        assertThat(h.toaster.shown).containsExactly("Couldn’t save that change — try again.")
    }
}
