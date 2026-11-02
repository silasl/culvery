package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.FILM_ID
import uk.co.siland.culvery.capability.party.FILM_NIGHT
import uk.co.siland.culvery.capability.party.KITCHEN
import uk.co.siland.culvery.capability.party.KITCHEN_BRIGHT
import uk.co.siland.culvery.capability.party.LOCKED_HEATER
import uk.co.siland.culvery.capability.party.LOUNGE
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.MusicChoice
import uk.co.siland.culvery.capability.party.PARTY_MODE
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.capability.party.grant
import uk.co.siland.culvery.core.house.AddedPlug
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.PlugOption
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

private val HEATER = PlugOption("heater", "Heater", needsPin = true)
private val LAMP = PlugOption("lamp", "Lamp", needsPin = false)
private val FAN = PlugOption("fan", "Fan", needsPin = false)

/** 15b §4.2: a stored mode's page, as it draws and as it saves, over the real actions and house with fake seams. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h2400dp-land-hdpi")
class StoredModePageTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = TestOverlay()
    private val scope = CoroutineScope(Job())
    private val toaster = RecordingToaster()
    private var rig = PageRig(scope)

    @After fun stop() = scope.cancel()

    /** Shows Settings › Modes with [modes] stored and the page of [open] showing; [read] false leaves the record unread. */
    private fun show(vararg modes: StoredMode, open: String = modes.last().id, read: Boolean = true) {
        runBlocking { rig.settings.update { ModesPrefs(true, modes.toList()) } }
        if (read) rig.loaded()
        val page = rig.page(toaster)
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.size(960.dp, 2400.dp)) {
                        InSettingsPane { page.Content() }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.waitFor { exists("modes_add") }
        tap("mode_row_$open")
        compose.waitFor { exists("mode_turn") }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Scrolls the page to [tag] if it is a page row, then taps it; a sheet's rows are in no scroll, so are only tapped. */
    private fun tap(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun gone(tag: String) = compose.onAllNodesWithTag(tag).assertCountEquals(0)

    private fun textOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    private fun saved(id: String = FILM_ID) = runBlocking { rig.settings.prefs.first().mode(id)!! }

    private fun turnOnFromOutside(id: String, grant: ModeGrant) = runBlocking { rig.house.turnOn(id, grant) }

    // Turning on and off

    /** Fails if the button doesn't call the tap with what it shows, or doesn't read "Turn off now" once on. */
    @Test
    fun turnOnNowTaps() {
        show(PARTY_MODE)
        compose.onNodeWithTag("mode_turn").assertTextEquals("Turn on now").performClick()

        compose.waitFor { rig.house.onMode.value == "party" }
        assertThat(rig.access.labels).containsExactly("Turn Party mode on")
        compose.waitFor { textOf("mode_turn") == "Turn off now" }
    }

    /** Fails if the line that says when changes apply is missing while the mode is on, or shown while it is off. */
    @Test
    fun changesApplyLineWhileOn() {
        show(PARTY_MODE)
        gone("mode_applies_line")
        turnOnFromOutside("party", ModeGrant.Person(grant(HousePermissions.STORED_MODE)))

        compose.waitFor { exists("mode_applies_line") }
        compose.onNodeWithTag("mode_applies_line").assertTextEquals("Changes apply the next time Party mode turns on.")
    }

    /** Fails if Holiday being on lets a stored mode turn on from its page, or the refusal isn't under the button. */
    @Test
    fun oneAtATimeFromThePage() {
        rig.holidayOn.value = true
        show(FILM_NIGHT)

        tap("mode_turn")

        compose.waitFor { exists("mode_turn_line") }
        compose.onNodeWithTag("mode_turn_line").assertTextEquals("Turn Holiday mode off first.")
        assertThat(rig.house.onMode.value).isNull()
    }

    /** 15b M2: fails if Turn on now crashes when the mode's PIN flag is saved between the tap's read and the turn-on. */
    @Test
    fun aPinFlagSavedMidTapDoesntCrash() {
        show(FILM_NIGHT)
        rig.plugs.onHoldsPin = { rig.settings.update { it.withMode(FILM_ID) { m -> m.copy(needsPin = true) } } }

        tap("mode_turn")

        compose.waitFor { saved().needsPin && rig.house.running.value == null }
        compose.waitForIdle()
        assertThat(rig.house.onMode.value).isNull()
        compose.onNodeWithTag("mode_turn").assertTextEquals("Turn on now")
    }

    /** 15b M2: fails if a double tap on Turn on now shows a second PIN pad after the first. */
    @Test
    fun aDoubleTapAsksForOnePin() {
        show(PARTY_MODE)
        val gate = CompletableDeferred<Unit>()
        rig.access.padGate = gate

        tap("mode_turn")
        tap("mode_turn")
        compose.waitForIdle()
        gate.complete(Unit)

        compose.waitFor { rig.house.onMode.value == "party" }
        compose.waitForIdle()
        assertThat(rig.access.asked).hasSize(1)
    }

    // Show on Home

    private fun supper() = FILM_NIGHT.copy(id = "modeaaaaaa", name = "Supper")

    private fun reading() = FILM_NIGHT.copy(id = "modebbbbbb", name = "Reading")

    /** Review Focus 3: fails if a full Home lets a hidden mode be switched on, or the line doesn't name the room. */
    @Test
    fun showOnHomeWhenFull() {
        show(PARTY_MODE, supper(), reading(), FILM_NIGHT.copy(showOnHome = false), open = FILM_ID)

        compose.onNodeWithTag("mode_home").assertIsOff().assertIsNotEnabled().assertTextContains("Home has room for 4 modes.")
        rig.room.value = 3
        compose.waitFor { textOf("mode_home").contains("3 modes") }
        compose.onNodeWithTag("mode_home").assertTextContains("Home has room for 3 modes.")
    }

    /** Fails if a mode with room can't be put on Home. */
    @Test
    fun aHiddenModeGoesOnHomeWhenThereIsRoom() {
        show(PARTY_MODE, FILM_NIGHT.copy(showOnHome = false))

        compose.onNodeWithTag("mode_home").assertIsOff().assertIsEnabled().performClick()

        compose.waitFor { saved().showOnHome }
        compose.onNodeWithTag("mode_home").assertIsOn()
    }

    /** Fails if a shown mode can't be taken off Home. */
    @Test
    fun aShownModeComesOffHome() {
        show(PARTY_MODE, FILM_NIGHT)

        compose.waitFor { runCatching { compose.onNodeWithTag("mode_home").assertIsOn() }.isSuccess }
        compose.onNodeWithTag("mode_home").assertIsEnabled()
        tap("mode_home")

        compose.waitFor { !saved().showOnHome }
        compose.onNodeWithTag("mode_home").assertIsOff()
    }

    /** Fails if a hidden mode can't turn on from its page, as its tile can't while it is off Home. */
    @Test
    fun aHiddenModeTurnsOnFromItsPage() {
        show(FILM_NIGHT.copy(showOnHome = false))

        tap("mode_turn")

        compose.waitFor { rig.house.onMode.value == FILM_ID }
    }

    /** Review Focus 3: fails if a mode stored as shown but past Home's room reads on. */
    @Test
    fun aShownModePastTheRoomReadsOff() {
        rig.room.value = 2
        show(PARTY_MODE, FILM_NIGHT)

        compose.waitFor { textOf("mode_home").contains("2 modes") }
        compose.onNodeWithTag("mode_home").assertIsOff().assertIsNotEnabled().assertTextContains("Home has room for 2 modes.")
        assertThat(saved().showOnHome).isTrue()
    }

    // Needs a PIN

    private fun addHeater() {
        compose.waitFor { exists("plug_add") }
        tap("plug_add")
        tap("plug_option_0")
    }

    /** Decision 6: fails if adding a plug that needs a PIN leaves the mode without one. */
    @Test
    fun aLockedPlugHoldsThePin() {
        rig.plugs.offered = listOf(HEATER, LAMP)
        show(FILM_NIGHT)
        compose.onNodeWithTag("mode_pin").assertIsOff().assertIsEnabled()

        addHeater()

        compose.waitFor { saved().needsPin }
        compose.waitFor { textOf("mode_pin").contains("A plug in this mode needs a PIN.") }
        compose.onNodeWithTag("mode_pin").assertIsOn().assertIsNotEnabled()
        assertThat(rig.plugs.chosen).containsExactly(Triple(FILM_ID, "heater", true))
    }

    /** Fails if the switch stays locked once the locked plug is gone. */
    @Test
    fun removingTheLastLockedPlugFreesTheSwitch() {
        rig.plugs.offered = listOf(HEATER, LAMP)
        show(FILM_NIGHT)
        addHeater()
        compose.waitFor { saved().needsPin && textOf("mode_pin").contains("needs a PIN") }

        tap("plug_remove_0")

        compose.waitFor { !textOf("mode_pin").contains("needs a PIN") }
        compose.onNodeWithTag("mode_pin").assertIsOn().assertIsEnabled()
    }

    /** Ruling 14: fails if a plug locked later, on the Home Assistant page, doesn't hold the PIN, or its tap doesn't ask. */
    @Test
    fun aPlugLockedLaterHoldsThePinToo() {
        rig = PageRig(scope, role = null)
        rig.plugs.offered = listOf(HEATER, LAMP)
        rig.plugs.addedNow.value = listOf(AddedPlug("heater", on = true, needsPin = false))
        show(FILM_NIGHT)
        compose.onNodeWithTag("mode_pin").assertIsOff().assertIsEnabled()

        rig.plugs.addedNow.value = listOf(LOCKED_HEATER)

        compose.waitFor { textOf("mode_pin").contains("A plug in this mode needs a PIN.") }
        compose.onNodeWithTag("mode_pin").assertIsOn().assertIsNotEnabled()
        tap("mode_turn")
        compose.waitFor { rig.access.asked.isNotEmpty() }
        assertThat(rig.access.asked).containsExactly(listOf("party.mode"))
        assertThat(rig.house.onMode.value).isNull()
    }

    /** U1: fails if switching the PIN off asks for a PIN. */
    @Test
    fun pinOffNeedsOnlyTheSession() {
        show(PARTY_MODE)

        compose.onNodeWithTag("mode_pin").assertIsOn().assertIsEnabled().performClick()

        compose.waitFor { !saved("party").needsPin }
        assertThat(rig.access.asked).isEmpty()
        compose.onNodeWithTag("mode_pin").assertIsOff()
    }

    // Lights

    /** Fails if a section doesn't say the rest are left alone (spoken as written, though drawn in capitals). */
    @Test
    fun lightsLabelSaysTheRestAreLeftAlone() {
        rig.plugs.offered = listOf(LAMP)
        show(FILM_NIGHT)

        compose.waitFor { compose.onAllNodes(hasContentDescription("Lights · every other light is left alone")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitFor { compose.onAllNodes(hasContentDescription("Plugs · every other plug is left alone")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Fails if "Add a light" offers a room the mode has, or none it hasn't. */
    @Test
    fun addALightListsOnlyRoomsNotAdded() {
        show(FILM_NIGHT)
        compose.waitFor { exists("light_add") }

        tap("light_add")

        compose.onNodeWithTag("light_room_0").assertTextEquals("Lounge")
        compose.onNodeWithTag("light_room_1").assertTextEquals("Hall")
        gone("light_room_2")
    }

    /** Ledger 1: fails if a room set to a scene, On or Off isn't drawn and saved as its own choice. */
    @Test
    fun aRoomTakesASceneOnOrOff() {
        show(FILM_NIGHT)
        compose.waitFor { exists("light_add") }
        compose.onNodeWithTag("light_chip_0").assertTextEquals("Scene: Bright")

        tap("light_chip_0")
        tap("light_scene_1")
        tap("light_save")
        compose.waitFor { textOf("light_chip_0") == "Scene: Relax" }

        tap("light_add")
        tap("light_room_0")
        tap("light_on")
        repeat(4) { tap("light_minus") }
        compose.onNodeWithTag("light_percent").assertTextEquals("60%")
        tap("light_save")
        compose.waitFor { exists("light_chip_1") }
        compose.onNodeWithTag("light_chip_1").assertTextEquals("On · 60%")

        tap("light_add")
        tap("light_room_0")
        tap("light_off")
        tap("light_save")
        compose.waitFor { exists("light_chip_2") }
        compose.onNodeWithTag("light_chip_2").assertTextEquals("Off")
        assertThat(saved().lights.map { it.choice }).containsExactly(LightChoice.Scene("s2", "Relax"), LightChoice.On(60), LightChoice.Off).inOrder()
        assertThat(saved().lights.map { it.roomName }).containsExactly("Kitchen", "Lounge", "Hall").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** S5: fails if a room that can't dim shows a percent on its chip. */
    @Test
    fun aRoomThatCantDimShowsOnWithNoPercent() {
        show(FILM_NIGHT.copy(lights = listOf(RoomChoice("r3", "Hall", LightChoice.On(100)))))
        compose.waitFor { exists("light_chip_0") }

        compose.onNodeWithTag("light_chip_0").assertTextEquals("On")
    }

    /** Fails if a light can't be taken out, or taking one out takes another. */
    @Test
    fun removeALight() {
        show(FILM_NIGHT.copy(lights = listOf(KITCHEN_BRIGHT, RoomChoice("r2", "Lounge", LightChoice.Off))))
        compose.waitFor { exists("light_remove_1") }

        tap("light_remove_0")

        compose.waitFor { saved().lights == listOf(RoomChoice("r2", "Lounge", LightChoice.Off)) }
        compose.waitFor { !exists("light_chip_1") }
        compose.onNodeWithTag("light_chip_0").assertTextEquals("Off")
    }

    // Plugs

    /** Fails if "Add a plug" offers a plug the mode has, or leaves out one it hasn't. */
    @Test
    fun addAPlugListsTabPlugsNotAdded() {
        rig.plugs.offered = listOf(HEATER, LAMP, FAN)
        rig.plugs.addedNow.value = listOf(AddedPlug("lamp", on = true, needsPin = false))
        show(FILM_NIGHT)
        compose.waitFor { exists("plug_row_0") && exists("plug_add") }

        tap("plug_add")

        compose.waitFor { exists("plug_add_sheet") }
        compose.onNodeWithTag("plug_option_0").assertTextEquals("Heater")
        compose.onNodeWithTag("plug_option_1").assertTextEquals("Fan")
        gone("plug_option_2")
    }

    /** Fails if the chip doesn't flip the plug between On and Off and say which. */
    @Test
    fun plugChipSwitchesOnAndOff() {
        rig.plugs.offered = listOf(LAMP)
        rig.plugs.addedNow.value = listOf(AddedPlug("lamp", on = true, needsPin = false))
        show(FILM_NIGHT)
        compose.waitFor { exists("plug_chip_0") }
        compose.onNodeWithTag("plug_chip_0").assertTextEquals("On")

        tap("plug_chip_0")
        compose.waitFor { textOf("plug_chip_0") == "Off" }
        tap("plug_chip_0")
        compose.waitFor { textOf("plug_chip_0") == "On" }

        assertThat(rig.plugs.chosen).containsExactly(Triple(FILM_ID, "lamp", false), Triple(FILM_ID, "lamp", true)).inOrder()
    }

    /** Fails if a plug that needs a PIN has no padlock, or one that doesn't has one. */
    @Test
    fun aPinPlugShowsThePadlock() {
        rig.plugs.offered = listOf(HEATER, LAMP)
        rig.plugs.addedNow.value = listOf(LOCKED_HEATER, AddedPlug("lamp", on = false, needsPin = false))
        show(FILM_NIGHT)
        compose.waitFor { exists("plug_row_1") }

        compose.onNodeWithTag("plug_lock_0").assertExists()
        gone("plug_lock_1")
    }

    // Music

    /** Fails if choosing None drops the speakers the mode kept. */
    @Test
    fun noMusicLeavesTheSpeakersAlone() {
        show(FILM_NIGHT.copy(speakers = listOf(KITCHEN, LOUNGE)))
        compose.waitFor { exists("mode_music") }
        compose.onNodeWithTag("mode_music").assertTextEquals("Music", "Party mix")

        tap("mode_music")
        tap("mode_music_0")

        compose.waitFor { saved().music == null }
        assertThat(saved().speakers?.map { it.name }).containsExactly("Kitchen", "Lounge").inOrder()
        compose.onNodeWithTag("mode_music").assertTextEquals("Music", "None")
    }

    /** Fails if a pick in the music sheet never reaches the store (the old theHostSavesThePick). */
    @Test
    fun pickingMusicReachesTheStore() {
        show(FILM_NIGHT.copy(music = null))
        compose.waitFor { exists("mode_music") }

        tap("mode_music")
        tap("mode_music_1")

        compose.waitFor { saved().music != null }
        assertThat(saved().music).isEqualTo(MusicChoice(SUNDAY.id, SUNDAY.name))
    }

    // Delete

    /** Review Focus 5: fails if the plugs aren't forgotten before the mode leaves the store, or the page stays up. */
    @Test
    fun deleteConfirmsThenGoes() {
        val stillThere = mutableListOf<Boolean>()
        rig.plugs.onForget = { stillThere += (rig.settings.prefs.first().mode(FILM_ID) != null) }
        show(PARTY_MODE, FILM_NIGHT)

        tap("mode_delete")
        compose.onNodeWithTag("mode_delete_confirm")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Delete Film night? Its music, lights and plugs choices go too."))
        gone("mode_delete")
        compose.onNodeWithTag("mode_delete_go").assertTextEquals("Delete mode")
        tap("mode_delete_go")

        compose.waitFor { exists("modes_add") }
        assertThat(rig.plugs.forgotten).containsExactly(FILM_ID)
        assertThat(stillThere).containsExactly(true)
        assertThat(runBlocking { rig.settings.prefs.first().modes.map { it.id } }).containsExactly("party")
        gone("mode_header")
        gone("mode_row_$FILM_ID")
    }

    /** Fails if a mode that is on can be deleted, or the refusal doesn't name the mode. */
    @Test
    fun deleteIsRefusedWhileOn() {
        show(FILM_NIGHT)
        turnOnFromOutside(FILM_ID, ModeGrant.Wall(FILM_ID))
        compose.waitFor { exists("mode_applies_line") }

        tap("mode_delete")

        compose.onNodeWithTag("mode_delete_line").assertTextEquals("Turn Film night off first.")
        gone("mode_delete_confirm")
        assertThat(rig.plugs.forgotten).isEmpty()
        assertThat(saved().name).isEqualTo("Film night")
    }

    /** Fails if a mode that is turning on can be deleted. */
    @Test
    fun deleteIsRefusedWhileARunIsGoing() {
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["music.start"] = gate
        show(FILM_NIGHT)
        val job = scope.launch { rig.house.turnOn(FILM_ID, ModeGrant.Wall(FILM_ID)) }
        compose.waitFor { rig.house.running.value != null }

        tap("mode_delete")

        compose.onNodeWithTag("mode_delete_line").assertTextEquals("Turn Film night off first.")
        gone("mode_delete_confirm")
        gate.complete(Unit)
        runBlocking { job.join() }
    }

    /** Fails if a mode whose record hasn't been read can be deleted. */
    @Test
    fun deleteIsRefusedWhileTheRecordIsUnread() {
        show(FILM_NIGHT, read = false)

        tap("mode_delete")

        compose.onNodeWithTag("mode_delete_line").assertTextEquals("Turn Film night off first.")
        gone("mode_delete_confirm")
    }

    /** Fails if a forget that throws still removes the mode, or says nothing. */
    @Test
    fun aFailedForgetKeepsTheMode() {
        rig.plugs.forgetThrows = true
        show(PARTY_MODE, FILM_NIGHT)
        tap("mode_delete")

        tap("mode_delete_go")

        compose.waitFor { toaster.shown.isNotEmpty() }
        assertThat(toaster.shown).containsExactly(COULD_NOT_SAVE)
        assertThat(saved().name).isEqualTo("Film night")
        compose.onNodeWithTag("mode_header").assertExists()
        compose.onNodeWithTag("mode_delete_confirm").assertExists()
    }

    /** Fails if Keep deletes anything or leaves the card up. */
    @Test
    fun keepChangesNothing() {
        show(PARTY_MODE, FILM_NIGHT)
        tap("mode_delete")

        tap("mode_delete_keep")

        gone("mode_delete_confirm")
        compose.onNodeWithTag("mode_delete").assertExists()
        assertThat(rig.plugs.forgotten).isEmpty()
        assertThat(saved().name).isEqualTo("Film night")
    }
}
