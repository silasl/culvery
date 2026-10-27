package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 12 §4.3: a plug's card, its words and what its button does. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugCardTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun show(plug: PlugState, needsPin: Boolean = false, switching: Boolean = false) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(360.dp, 180.dp)) {
                PlugCard(plug, 0, needsPin, switching, onSwitch = { calls += "switch" }, onSettings = { calls += "settings" })
            }
        }
    }

    private fun card() = compose.onNodeWithTag("plug_card_0", useUnmergedTree = true)

    private fun status() = compose.onNodeWithTag("plug_card_0_status", useUnmergedTree = true)

    private fun isButton() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Fails if the name, "On · 182 W", the spoken group or the button's name and role differ from the spec. */
    @Test
    fun anOnPlugReadsAsTheSpecSays() {
        show(LAMP_ON)
        card().assertContentDescriptionEquals("Plug Lamp, on, 182 watts.")
        compose.onNodeWithTag("plug_card_0_name", useUnmergedTree = true).assertTextEquals("Lamp")
        status().assertTextEquals("On · 182 W")
        compose.onNodeWithTag("plug_card_0_switch").assertContentDescriptionEquals("Switch off").assert(isButton())
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the PIN setting isn't read out on the button or the lock doesn't show. */
    @Test
    fun anOffPinPlugSaysItNeedsAPin() {
        show(FAN_OFF, needsPin = true)
        compose.onNodeWithTag("plug_card_0_switch").assertContentDescriptionEquals("Switch on, needs a PIN")
        compose.onNodeWithTag("plug_card_0_lock", useUnmergedTree = true).assertIsDisplayed()
        card().assertContentDescriptionEquals("Plug Fan, off.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if an unreachable plug's button can be tapped, or the lock shows with no PIN setting. */
    @Test
    fun anUnreachablePlugsButtonIsInactive() {
        show(FAIRY_LOST)
        status().assertTextEquals("Can’t reach this plug.")
        card().assertContentDescriptionEquals("Plug Fairy lights, can’t reach this plug.")
        compose.onNodeWithTag("plug_card_0_switch").assertIsNotEnabled().performClick()
        assertThat(calls).isEmpty()
        compose.onAllNodesWithTag("plug_card_0_lock", useUnmergedTree = true).assertCountEquals(0)
    }

    /** Fails if a gone plug isn't told to be removed or added again, or its button works. */
    @Test
    fun aGonePlugSaysRemoveItOrAddItAgain() {
        show(PlugState(FAIRY, "Fairy lights", PlugPower.GONE, null))
        status().assertTextEquals("Home Assistant has no plug called that any more. Remove it or add it again.")
        card().assertContentDescriptionEquals("Plug Fairy lights. Home Assistant has no plug called that any more. Remove it or add it again.")
        compose.onNodeWithTag("plug_card_0_switch").assertIsNotEnabled().performClick()
        assertThat(calls).isEmpty()
    }

    /** Fails if a plug that hasn't been read shows anything but "Checking…" or can be switched. */
    @Test
    fun aPlugNotReadYetSaysChecking() {
        show(PlugState(FAN_OFF.entityId, "Fan", PlugPower.UNKNOWN, null))
        status().assertTextEquals("Checking…")
        compose.onNodeWithTag("plug_card_0_switch").assertIsNotEnabled()
    }

    /** Fails if "Switching…" doesn't show while the switch is out, or a tap during it reaches the switch. */
    @Test
    fun switchingShowsUntilTheStateComes() {
        show(DESK_OFF, switching = true)
        compose.onNodeWithTag("plug_card_0_switch").assertContentDescriptionEquals("Switching…").performClick()
        compose.onNodeWithText("Switching…", useUnmergedTree = true).assertIsDisplayed()
        assertThat(calls).isEmpty()
    }

    /** Fails if a tap on a plain plug's button doesn't switch once. */
    @Test
    fun aTapSwitches() {
        show(LAMP_ON)
        compose.onNodeWithTag("plug_card_0_switch").performClick()
        assertThat(calls).containsExactly("switch")
    }

    /** Fails if the › isn't named for its plug or doesn't open its settings. */
    @Test
    fun theSettingsButtonNamesThePlug() {
        show(LAMP_ON)
        compose.onNodeWithTag("plug_card_0_settings").assertContentDescriptionEquals("Settings for Lamp").performClick()
        assertThat(calls).containsExactly("settings")
        compose.assertClickablesHaveRoleAndLabel()
    }
}
