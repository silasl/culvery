package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.PartyRun
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice

/** 13 §2.6: the Party mode tile in the One-tap tray. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class PartyTileTest {
    @get:Rule val compose = createComposeRule()
    private var taps = 0
    private var on by mutableStateOf(false)
    private var line by mutableStateOf<String?>(null)
    private var running by mutableStateOf<PartyRun?>(null)

    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.background(Culvery.colors.bg)) {
                    HhTrayColumn("One-tap", listOf("party" to { PartyTile(on, line, running) { taps++ } }), Modifier.width(333.dp))
                }
            }
        }
    }

    private fun clickLabel() = compose.onNodeWithTag("party_tile").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    /** Fails if the words, what TalkBack hears or its click label differ from the spec. */
    @Test
    fun offReadsOff() {
        show()
        compose.onNodeWithTag("party_tile").assertIsDisplayed()
        compose.onNodeWithText("Party mode", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("party_tile_state", useUnmergedTree = true).assertTextEquals("Off")
        compose.onNode(hasContentDescription("Party mode, off.")).assertExists()
        assertThat(clickLabel()).isEqualTo("Turn on")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("party_tile").performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun onReadsOn() {
        on = true
        show()
        compose.onNodeWithTag("party_tile_state", useUnmergedTree = true).assertTextEquals("On")
        compose.onNode(hasContentDescription("Party mode, on.")).assertExists()
        assertThat(clickLabel()).isEqualTo("Turn off")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tap while a change runs reaches [onTap], or the words don't say what is happening. */
    @Test
    fun runningShowsAndIgnoresTaps() {
        running = PartyRun.TURNING_ON
        show()
        compose.onNodeWithTag("party_tile_state", useUnmergedTree = true).assertTextEquals("Turning on…")
        compose.onNode(hasContentDescription("Party mode, turning on.")).assertExists()
        compose.onNodeWithTag("party_tile").performClick()
        assertThat(taps).isEqualTo(0)

        on = true
        running = PartyRun.TURNING_OFF
        compose.waitForIdle()
        compose.onNodeWithTag("party_tile_state", useUnmergedTree = true).assertTextEquals("Turning off…")
        compose.onNode(hasContentDescription("Party mode, turning off.")).assertExists()
        compose.onNodeWithTag("party_tile").performClick()
        assertThat(taps).isEqualTo(0)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** What didn't happen shows under the tile, one line each, and goes when it is cleared. */
    @Test
    fun theLinesShowUnderIt() {
        on = true
        line = "The music didn’t start. Check the Sonos app.\nSome lights didn’t change: Kitchen."
        show()
        compose.onNodeWithTag("party_tile_line")
            .assertTextEquals("The music didn’t start. Check the Sonos app.\nSome lights didn’t change: Kitchen.")
        val tile = compose.onNodeWithTag("party_tile").getUnclippedBoundsInRoot()
        val under = compose.onNodeWithTag("party_tile_line").getUnclippedBoundsInRoot()
        assertThat(under.top).isAtLeast(tile.bottom)
        line = null
        compose.waitForIdle()
        compose.onNodeWithTag("party_tile_line").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
