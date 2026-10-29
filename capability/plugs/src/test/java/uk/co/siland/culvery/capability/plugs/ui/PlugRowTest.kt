package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.HOLIDAY
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 12 §4.3: a plug's row, its words, its button and its mode cells. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugRowTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun show(
        plug: PlugState,
        needsPin: Boolean = false,
        switching: Boolean = false,
        modes: List<HouseModeInfo> = listOf(HOLIDAY_MODE),
        choices: Map<String, ModeChoice> = emptyMap(),
        editing: Boolean = false,
        width: Int = 1116,
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.width(width.dp)) {
                PlugRow(plug, needsPin, switching, modes, { choices[it] ?: ModeChoice.IGNORE }, editing, onSwitch = { calls += "switch" }, onChoose = { m, c -> calls += "$m $c" })
            }
        }
    }

    private fun row(id: String = LAMP) = compose.onNodeWithTag("plug_$id", useUnmergedTree = true)

    private fun status(id: String = LAMP) = compose.onNodeWithTag("plug_${id}_status", useUnmergedTree = true)

    private fun button(id: String = LAMP) = compose.onNodeWithTag("plug_${id}_switch")

    private fun isButton() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun selected(on: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, on)

    /** Fails if the name, "On · 182 W", the spoken row or the button's name and role differ from the spec. */
    @Test
    fun onAndOffShowTheirButton() {
        show(LAMP_ON)
        row().assertContentDescriptionEquals("Plug Lamp, on, 182 watts.")
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).assertTextEquals("Lamp")
        status().assertTextEquals("On · 182 W")
        button().assertContentDescriptionEquals("Switch off").assert(isButton())
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if an off plug's row doesn't read "Off" with "Switch on" and the PIN setting on its button. */
    @Test
    fun anOffPlugOffersSwitchOn() {
        show(FAN_OFF.copy(entityId = LAMP), needsPin = true)
        row().assertContentDescriptionEquals("Plug Fan, off.")
        status().assertTextEquals("Off")
        button().assertContentDescriptionEquals("Switch on, needs a PIN").assert(isButton())
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tap on the button doesn't switch once. */
    @Test
    fun aTapSwitches() {
        show(LAMP_ON)
        button().performClick()
        assertThat(calls).containsExactly("switch")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if any of these shows a button or a lock, or reads or says anything but the spec's words. */
    @Test
    fun unknownNoLinkUnreachableAndGoneHaveNoButton() {
        val cases = listOf(
            Triple(PlugPower.UNKNOWN, "Checking…", "Plug Fan, checking."),
            Triple(PlugPower.NO_LINK, "Can’t reach Home Assistant.", "Plug Fan, can’t reach Home Assistant."),
            Triple(PlugPower.UNREACHABLE, "Can’t reach this plug.", "Plug Fan, can’t reach this plug."),
            Triple(
                PlugPower.GONE, "Home Assistant has no plug called that any more. Turn it off in Settings.",
                "Plug Fan. Home Assistant has no plug called that any more. Turn it off in Settings.",
            ),
        )
        var shown by mutableStateOf(PlugState(FAN, "Fan", PlugPower.UNKNOWN, null))
        compose.setContent { CulveryTheme(dark = true) { PlugRow(shown, true, false, emptyList(), { ModeChoice.IGNORE }, false, {}, { _, _ -> }) } }
        for ((power, line, spoken) in cases) {
            shown = PlugState(FAN, "Fan", power, null)
            compose.waitForIdle()
            status(FAN).assertTextEquals(line)
            row(FAN).assertContentDescriptionEquals(spoken)
            compose.onAllNodesWithTag("plug_${FAN}_switch").assertCountEquals(0)
            compose.onAllNodesWithTag("plug_${FAN}_lock", useUnmergedTree = true).assertCountEquals(0)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a name that needs two lines is cut to one, ellipsised, or shortened. */
    @Test
    fun aLongNameWrapsToTwoLines() {
        val name = "Fairy lights above the bookcase"
        show(PlugState(LAMP, name, PlugPower.OFF, null), width = 760)
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).assertTextEquals(name)
        val layout = textLayout()
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.isLineEllipsized(1)).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a name that needs more than two lines, with two mode columns in Edit, is ellipsised. */
    @Test
    fun aVeryLongNameIsNeverCutShort() {
        val name = "Living room standard lamp by the window next to the long bookcase and the old piano"
        show(PlugState(LAMP, name, PlugPower.OFF, null), modes = listOf(HOLIDAY_MODE, PARTY_MODE), editing = true, width = 760)
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).assertTextEquals(name)
        val layout = textLayout()
        assertThat(layout.lineCount).isGreaterThan(2)
        assertThat((0 until layout.lineCount).any { layout.isLineEllipsized(it) }).isFalse()
    }

    /** Fails if a short name is ellipsised or takes two lines. */
    @Test
    fun aShortNameStaysOnOneLine() {
        show(LAMP_ON, width = 760)
        val layout = textLayout()
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.isLineEllipsized(0)).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Fails if the lock is missing when the plug needs a PIN, shows when it doesn't, or is spoken. */
    @Test
    fun thePadlockShowsWhenNeedsPin() {
        show(LAMP_ON, needsPin = true)
        compose.onNodeWithTag("plug_${LAMP}_lock", useUnmergedTree = true).assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the lock shows for a plug with no PIN setting. */
    @Test
    fun noPinNoPadlock() {
        show(LAMP_ON)
        compose.onAllNodesWithTag("plug_${LAMP}_lock", useUnmergedTree = true).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if "Switching…" doesn't show while the switch is out, or a tap during it reaches the switch. */
    @Test
    fun switchingShowsUntilTheStateComes() {
        show(LAMP_ON, switching = true)
        button().assertContentDescriptionEquals("Switching…").performClick()
        assertThat(calls).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a cell at rest has anything to tap, or doesn't show and speak the plug's word for the mode. */
    @Test
    fun atRestTheCellIsReadOnly() {
        show(LAMP_OFF, choices = mapOf(HOLIDAY to ModeChoice.OFF))
        compose.onNodeWithTag("mode_${HOLIDAY}_$LAMP", useUnmergedTree = true).assertTextEquals("Off").assertContentDescriptionEquals("Holiday mode, off")
        compose.onAllNodesWithTag("mode_${HOLIDAY}_${LAMP}_on").assertCountEquals(0)
        compose.onAllNodesWithTag("mode_${HOLIDAY}_$LAMP", useUnmergedTree = true).fetchSemanticsNodes().single().let {
            assertThat(it.config.contains(SemanticsActions.OnClick)).isFalse()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a mode with no choice yet doesn't read Ignore. */
    @Test
    fun noChoiceReadsIgnore() {
        show(LAMP_OFF)
        compose.onNodeWithTag("mode_${HOLIDAY}_$LAMP", useUnmergedTree = true).assertTextEquals("Ignore").assertContentDescriptionEquals("Holiday mode, ignore")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if Edit doesn't offer three radio chips with the chosen one selected, spoken as the row, or a tap doesn't report the choice. */
    @Test
    fun inEditTheChipsChoose() {
        show(LAMP_OFF, choices = mapOf(HOLIDAY to ModeChoice.OFF), editing = true)
        val tag = "mode_${HOLIDAY}_$LAMP"
        compose.onNodeWithTag("${tag}_off").assert(selected(true))
        compose.onNodeWithTag("${tag}_on").assert(selected(false))
        compose.onNodeWithTag("${tag}_ignore").assert(selected(false))
        compose.onNodeWithTag("${tag}_on").assertContentDescriptionEquals("Holiday mode, Lamp, on")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        assertThat(calls).containsExactly("holiday ON")
        compose.onNodeWithTag("plug_$LAMP").assert(hasAnyDescendant(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        compose.assertClickablesHaveRoleAndLabel()
    }
}
