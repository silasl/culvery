package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 15a §4.5: a plug's tile, its words and its button. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugTileTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun show(plug: PlugState, needsPin: Boolean = false, switching: Boolean = false) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.width(270.dp)) { PlugTile(plug, needsPin, switching, onSwitch = { calls += "switch" }) }
        }
    }

    private fun tile(id: String = LAMP) = compose.onNodeWithTag("plug_$id", useUnmergedTree = true)

    private fun status(id: String = LAMP) = compose.onNodeWithTag("plug_${id}_status", useUnmergedTree = true)

    private fun button(id: String = LAMP) = compose.onNodeWithTag("plug_${id}_switch")

    private fun isButton() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Fails if the name, "On · 182 W", the spoken tile or the button's name and role differ from the spec. */
    @Test
    fun onAndOffShowTheirButton() {
        show(LAMP_ON)
        tile().assertContentDescriptionEquals("Plug Lamp, on, 182 watts.")
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).assertTextEquals("Lamp")
        status().assertTextEquals("On · 182 W")
        button().assertContentDescriptionEquals("Switch off").assert(isButton())
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if an off plug's tile doesn't read "Off" with "Switch on" and the PIN setting on its button. */
    @Test
    fun anOffPlugOffersSwitchOn() {
        show(FAN_OFF.copy(entityId = LAMP), needsPin = true)
        tile().assertContentDescriptionEquals("Plug Fan, off.")
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
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.width(270.dp)) { PlugTile(shown, true, false, {}) } } }
        for ((power, line, spoken) in cases) {
            shown = PlugState(FAN, "Fan", power, null)
            compose.waitForIdle()
            status(FAN).assertTextEquals(line)
            tile(FAN).assertContentDescriptionEquals(spoken)
            compose.onAllNodesWithTag("plug_${FAN}_switch").assertCountEquals(0)
            compose.onAllNodesWithTag("plug_${FAN}_lock", useUnmergedTree = true).assertCountEquals(0)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the lock is missing when the plug needs a PIN, or is spoken. */
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

    /** Fails if "Switching…" isn't both shown and spoken while the switch is out, or a tap during it reaches the switch. */
    @Test
    fun switchingShowsUntilTheStateComes() {
        show(LAMP_ON, switching = true)
        compose.onNodeWithText("Switching…", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("Switch off", useUnmergedTree = true).assertCountEquals(0)
        button().assertContentDescriptionEquals("Switching…").performClick()
        assertThat(calls).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the pill is missing its visible "Switch on" or "Switch off", or isn't at the bottom right of a tile with no padlock. */
    @Test
    fun theLabelledPillSitsAtTheBottomRight() {
        show(LAMP_ON)
        compose.onNodeWithText("Switch off", useUnmergedTree = true).assertIsDisplayed()
        val tile = tile().fetchSemanticsNode().boundsInRoot
        val pill = button().fetchSemanticsNode().boundsInRoot
        val d = compose.density.density
        assertThat(pill.bottom).isWithin(2f).of(tile.bottom - 15 * d)
        assertThat(pill.right).isWithin(2f).of(tile.right - 17 * d)
        assertThat(pill.height).isAtLeast(48 * d - 1)
    }

    private fun nameLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Fails if a name that needs two lines is cut to one, ellipsised or shortened. */
    @Test
    fun aLongNameWrapsToTwoLines() {
        val name = "Reading lamp on the bedside table upstairs"
        show(PlugState(LAMP, name, PlugPower.OFF, null))
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).assertTextEquals(name)
        val layout = nameLayout()
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.isLineEllipsized(1)).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a name that needs more than two lines is ellipsised, or the pill ends up outside the tile. */
    @Test
    fun aVeryLongNameIsNeverCutShort() {
        val name = "Living room standard lamp by the window next to the long bookcase and the old piano"
        show(PlugState(LAMP, name, PlugPower.OFF, null))
        compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).assertTextEquals(name)
        val layout = nameLayout()
        assertThat(layout.lineCount).isGreaterThan(2)
        assertThat((0 until layout.lineCount).any { layout.isLineEllipsized(it) }).isFalse()
        val tile = tile().fetchSemanticsNode().boundsInRoot
        assertThat(button().fetchSemanticsNode().boundsInRoot.bottom).isAtMost(tile.bottom)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a short name is ellipsised or takes two lines. */
    @Test
    fun aShortNameStaysOnOneLine() {
        show(LAMP_ON)
        val layout = nameLayout()
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.isLineEllipsized(0)).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a short name's pill sits anywhere but the tile's bottom right, or beside the name instead of under it. */
    @Test
    fun aShortNamesPillSitsBottomRightUnderTheName() {
        show(LAMP_ON)
        val tile = tile().fetchSemanticsNode().boundsInRoot
        val pill = button().fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithTag("plug_${LAMP}_name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertThat(tile.right - pill.right).isLessThan(tile.width / 10)
        assertThat(pill.top).isAtLeast(name.bottom)
    }
}
