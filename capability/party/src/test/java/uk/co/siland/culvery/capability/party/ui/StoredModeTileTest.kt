package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.FILM_ID
import uk.co.siland.culvery.capability.party.FILM_NIGHT
import uk.co.siland.culvery.capability.party.HouseRig
import uk.co.siland.culvery.capability.party.ModeRun
import uk.co.siland.culvery.capability.party.StoredModeActions
import uk.co.siland.culvery.capability.party.TestAccess
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.test.OneTapTray
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice

/** 15b §4.3: a stored mode's tile in the One-tap tray. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class StoredModeTileTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(Job())
    private var taps = 0
    private var on by mutableStateOf(false)
    private var line by mutableStateOf<String?>(null)
    private var running by mutableStateOf<ModeRun?>(null)

    @After fun stop() = scope.cancel()

    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.background(Culvery.colors.bg)) {
                    OneTapTray(FILM_ID, line = line, lineTag = storedModeTileLineTag(FILM_ID)) { StoredModeTile(FILM_ID, "Film night", Icons.BEDTIME, on, running) { taps++ } }
                }
            }
        }
    }

    private fun clickLabel() = compose.onNodeWithTag(TILE).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    /** Fails if the words, what TalkBack hears or its click label differ from the spec. */
    @Test
    fun offShowsNoStateLine() {
        show()
        compose.onNodeWithTag(TILE).assertIsDisplayed()
        compose.onNodeWithText("Film night", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("${TILE}_state", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("${TILE}_on", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasContentDescription("Film night, off.")).assertExists()
        assertThat(clickLabel()).isEqualTo("Turn on")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag(TILE).performClick()
        assertThat(taps).isEqualTo(1)
    }

    /** Fails if a lit tile doesn't show the "ON" pill, say on to TalkBack, or offer to turn off. */
    @Test
    fun onShowsTheOnPill() {
        on = true
        show()
        compose.onNodeWithTag("${TILE}_on", useUnmergedTree = true).assertTextEquals("ON")
        compose.onNode(hasContentDescription("Film night, on.")).assertExists()
        assertThat(clickLabel()).isEqualTo("Turn off")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 15a §2.1. Fails if the tile's own fill stops short of its row's height. */
    @Test
    fun theTileFillsItsRow() {
        show()
        val row = compose.onNodeWithTag("tray_row_$FILM_ID").getUnclippedBoundsInRoot()
        val tile = compose.onNodeWithTag(TILE).getUnclippedBoundsInRoot()
        assertThat(tile.height.value).isWithin(1f).of(row.height.value)
    }

    /** Fails if a tap while a change runs reaches the action, or the words don't say what is happening. */
    @Test
    fun runningShowsTurningOn() {
        running = ModeRun.TURNING_ON
        show()
        compose.onNodeWithTag("${TILE}_state", useUnmergedTree = true).assertTextEquals("Turning on…")
        compose.onNode(hasContentDescription("Film night, turning on.")).assertExists()
        compose.onNodeWithTag(TILE).performClick()
        assertThat(taps).isEqualTo(0)

        on = true
        running = ModeRun.TURNING_OFF
        compose.waitForIdle()
        compose.onNodeWithTag("${TILE}_state", useUnmergedTree = true).assertTextEquals("Turning off…")
        compose.onNode(hasContentDescription("Film night, turning off.")).assertExists()
        compose.onNodeWithTag(TILE).performClick()
        assertThat(taps).isEqualTo(0)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** What didn't happen shows under the tile, one line each, and goes when it is cleared. */
    @Test
    fun theLineShows() {
        on = true
        line = "The music didn’t start. Check the Sonos app.\nSome lights didn’t change: Kitchen."
        show()
        compose.onNodeWithTag("${TILE}_line")
            .assertTextEquals("The music didn’t start. Check the Sonos app.\nSome lights didn’t change: Kitchen.")
        val tile = compose.onNodeWithTag(TILE).getUnclippedBoundsInRoot()
        val under = compose.onNodeWithTag("${TILE}_line").getUnclippedBoundsInRoot()
        assertThat(under.top).isAtLeast(tile.bottom)
        line = null
        compose.waitForIdle()
        compose.onNodeWithTag("${TILE}_line").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 15b §4.3: fails if a tap on a no-PIN mode opens a sheet, or doesn't turn the mode on and light the tile. */
    @Test
    fun aTapTurnsOnWithNoSheet() {
        val rig = HouseRig(scope)
        runBlocking {
            rig.choose(FILM_NIGHT)
            rig.loaded()
        }
        val actions = StoredModeActions(TestAccess(Role.CHILD), rig.house, rig.settings, setOf(rig.house))
        compose.setContent {
            CulveryTheme(dark = true) {
                OneTapTray(FILM_ID) { StoredModeTileHost(FILM_NIGHT.id, FILM_NIGHT.name, FILM_NIGHT.icon, actions, rig.house) }
            }
        }

        compose.onNodeWithTag(TILE).performClick()
        compose.waitUntil(5_000) { rig.house.onMode.value == FILM_ID }
        compose.waitForIdle()

        compose.onNodeWithTag("party_sheet").assertDoesNotExist()
        compose.onNodeWithTag("${TILE}_on", useUnmergedTree = true).assertTextEquals("ON")
    }

    private companion object {
        const val TILE = "mode_tile_$FILM_ID"
    }
}
