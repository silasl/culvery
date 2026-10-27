package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 12 §4.3: a plug's settings: the PIN setting and Remove. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugSettingsSheetTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun show(needsPin: Boolean) = compose.setContent {
        CulveryTheme(dark = true) {
            PlugSettingsSheet(LAMP_ON, needsPin, onNeedsPin = { calls += "pin $it" }, onRemove = { calls += "remove" }, onClose = { calls += "close" })
        }
    }

    /** Fails if the plug's name isn't the title, or a tap on the PIN row doesn't ask for the opposite. */
    @Test
    fun thePinSettingSwitches() {
        show(needsPin = false)
        compose.onNodeWithText("Lamp").assertTextEquals("Lamp")
        compose.onNodeWithText("Needs a PIN to switch").assertExists()
        compose.onNodeWithTag("plug_settings_pin").assertIsOff().performClick()
        assertThat(calls).containsExactly("pin true")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a plug that needs a PIN doesn't show the setting on, or a tap doesn't ask to turn it off. */
    @Test
    fun anOnSettingTurnsOff() {
        show(needsPin = true)
        compose.onNodeWithTag("plug_settings_pin").assertIsOn().performClick()
        assertThat(calls).containsExactly("pin false")
    }

    /** Fails if Remove doesn't remove, then close, once. */
    @Test
    fun removeRemovesAndCloses() {
        show(needsPin = false)
        compose.onNodeWithTag("plug_settings_remove").assertTextEquals("Remove").performClick()
        assertThat(calls).containsExactly("remove", "close").inOrder()
    }
}
