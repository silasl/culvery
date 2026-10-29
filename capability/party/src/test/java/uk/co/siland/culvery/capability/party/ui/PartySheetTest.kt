package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.HouseRig
import uk.co.siland.culvery.capability.party.PartyActions
import uk.co.siland.culvery.capability.party.PartyPlan
import uk.co.siland.culvery.capability.party.PartyRecord
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.capability.party.PARTY_MIX
import uk.co.siland.culvery.capability.party.KITCHEN_BRIGHT
import uk.co.siland.culvery.capability.party.LOUNGE_RELAX
import uk.co.siland.culvery.capability.party.MADE
import uk.co.siland.culvery.capability.party.LIGHTS_BEFORE
import uk.co.siland.culvery.capability.party.TestAccess
import uk.co.siland.culvery.capability.party.TestSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.waitFor

/** 13 §2.6: the sheet a tap opens, on and off. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class PartySheetTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rig = HouseRig(scope)
    private val access = TestAccess(Role.ADULT)
    private val holiday = MutableStateFlow<Boolean?>(false)
    private val overlay = TestOverlay()
    private var confirms = 0
    private var cancels = 0

    @After
    fun stop() = scope.cancel()

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun show(turningOn: Boolean, lines: List<String>) {
        compose.setContent {
            CulveryTheme(dark = true) {
                Row(Modifier.background(Culvery.colors.bg)) {
                    PartySheet(turningOn, lines, onConfirm = { confirms++ }, onCancel = { cancels++ })
                }
            }
        }
    }

    private fun lines(): List<String> = compose.onNodeWithTag("party_sheet_lines").fetchSemanticsNode().children
        .map { it.config[SemanticsProperties.Text].joinToString("") }

    /** Fails if the lines aren't listed in the order they run under the title, or Turn Party mode on doesn't confirm once. */
    @Test
    fun theOnSheetListsWhatWillHappen() {
        val will = PartyWords.willLines(
            PartyPlan(listOf(PlugChange("Lamp", false)), PARTY_MIX, listOf(KITCHEN_BRIGHT, LOUNGE_RELAX)),
        )
        show(true, will)

        compose.onNodeWithText("Party mode will:", useUnmergedTree = true).assertExists()
        assertThat(lines()).containsExactly(
            "Switch Lamp off.", "Plugs go back as they were when you turn it off.", "Play Party mix on every speaker.",
            "Bright in Kitchen.", "Relax in Lounge.",
        ).inOrder()
        compose.onNodeWithText("Turn Party mode on", useUnmergedTree = true).assertExists()
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("party_sheet_confirm").performClick()
        assertThat(confirms).isEqualTo(1)
        assertThat(cancels).isEqualTo(0)
    }

    @Test
    fun nothingToDoStillTurnsOn() {
        show(true, PartyWords.willLines(PartyPlan(emptyList(), null, emptyList())))

        assertThat(lines()).containsExactly("Party mode has nothing to do yet. Choose plugs, music and lights in Settings › Party mode.")
        compose.onNodeWithText("Turn Party mode on", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("party_sheet_confirm").performClick()
        assertThat(confirms).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theOffSheetListsWhatGoesBack() {
        val back = PartyWords.backLines(PartyRecord(on = true, made = MADE, lights = LIGHTS_BEFORE), plugsListed = true)
        show(false, back)

        compose.onNodeWithText("When Party mode ends:", useUnmergedTree = true).assertExists()
        assertThat(lines()).containsExactly(
            "Stop the music and put the speakers back in their groups.", "Put the plugs back as they were.",
            "Put the lights back in Kitchen and Lounge.", "Anything changed by hand during the party stays as it is.",
        ).inOrder()
        compose.onNodeWithText("Turn Party mode off", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Turn Party mode on", useUnmergedTree = true).assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("party_sheet_confirm").performClick()
        assertThat(confirms).isEqualTo(1)
    }

    @Test
    fun cancelClosesWithoutTurning() {
        show(true, listOf("Bright in Kitchen."))

        compose.onNodeWithTag("party_sheet_cancel").performClick()

        assertThat(cancels).isEqualTo(1)
        assertThat(confirms).isEqualTo(0)
    }

    private fun open() {
        val actions = PartyActions(access, rig.house, setOf(TestSwitch(HouseModes.HOLIDAY, "Holiday mode", holiday)))
        runBlocking {
            rig.loaded()
            rig.choose()
        }
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Row(Modifier.background(Culvery.colors.bg)) {
                        HhTrayColumn("One-tap", listOf("party" to { PartyTileHost(actions, rig.house) }), Modifier.width(333.dp))
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.onNodeWithTag("party_tile").performClick()
        compose.waitFor { has("party_sheet") && shows("Bright in Kitchen.") }
    }

    /** Fails if the confirm doesn't turn Party on with the PIN's grant and close the sheet. */
    @Test
    fun theHostTurnsOnThroughTheActions() {
        open()
        assertThat(access.asked).containsExactly(listOf("party.mode"))

        compose.onNodeWithTag("party_sheet_confirm").performClick()
        compose.waitFor { overlay.content == null }

        assertThat(rig.house.isOn.value).isTrue()
        assertThat(rig.plugs.begins.single().granted).contains(HousePermissions.PARTY_MODE)
        assertThat(rig.music.startedWith).hasSize(1)
        compose.waitForIdle()
        compose.onNodeWithTag("party_tile_state", useUnmergedTree = true).assertTextEquals("On")
    }

    /** Review Focus 5. Fails if Holiday mode turned on while the sheet was open lets the confirm turn Party on, or the tile doesn't say why. */
    @Test
    fun aConfirmAfterHolidayTurnedOnTurnsNothingOn() {
        open()
        holiday.value = true

        compose.onNodeWithTag("party_sheet_confirm").performClick()
        compose.waitFor { overlay.content == null }

        assertThat(rig.trace.calls).isEmpty()
        assertThat(rig.house.isOn.value).isFalse()
        compose.waitFor { has("party_tile_line") }
        compose.onNodeWithTag("party_tile_line").assertTextEquals("Turn Holiday mode off first.")
    }

    /** The off sheet turns Party off through the actions and closes. */
    @Test
    fun theHostTurnsOffThroughTheActions() {
        runBlocking {
            rig.loaded()
            rig.choose()
            rig.house.turnOn(access.authorise(HousePermissions.PARTY_MODE)!!)
        }
        val actions = PartyActions(access, rig.house, emptySet())
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Row(Modifier.background(Culvery.colors.bg)) {
                        HhTrayColumn("One-tap", listOf("party" to { PartyTileHost(actions, rig.house) }), Modifier.width(333.dp))
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.onNodeWithTag("party_tile").performClick()
        compose.waitFor { has("party_sheet") && shows("Turn Party mode off") }

        compose.onNodeWithTag("party_sheet_confirm").performClick()
        compose.waitFor { overlay.content == null }

        assertThat(rig.house.isOn.value).isFalse()
        assertThat(rig.music.stops).hasSize(1)
    }
}
