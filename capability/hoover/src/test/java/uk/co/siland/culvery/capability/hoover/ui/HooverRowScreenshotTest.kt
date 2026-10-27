package uk.co.siland.culvery.capability.hoover.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.hoover.HooverActivity
import uk.co.siland.culvery.capability.hoover.HooverState
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.snap

/** The Hoover row in the One-tap tray at 333 dp (11 §4.4), dark and light. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class HooverRowScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, state: HooverState, line: String? = null, starting: Boolean = false) =
        compose.snap(name, dark, Modifier.padding(16.dp)) {
            HhTrayColumn("One-tap", listOf("hoover" to { HooverRow(state, line, starting) {} }), Modifier.width(333.dp))
        }

    private val docked = HooverState(HooverActivity.DOCKED, 86, null)
    private val cleaning = HooverState(HooverActivity.CLEANING, 60, null)
    private val stuck = HooverState(HooverActivity.ERROR, 40, null)
    private val cantReach = HooverState(null, null, "Can’t reach Home Assistant. Check the NAS is on.")
    private val tokenRefused = HooverState(null, null, "Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations.")
    private val notStarted = "The hoover didn’t start. Check the Eufy app."

    @Test fun dockedDark() = snap("hoover_row_docked_dark", true, docked)

    @Test fun dockedLight() = snap("hoover_row_docked_light", false, docked)

    @Test fun cleaningDark() = snap("hoover_row_cleaning_dark", true, cleaning)

    @Test fun cleaningLight() = snap("hoover_row_cleaning_light", false, cleaning)

    @Test fun startingDark() = snap("hoover_row_starting_dark", true, docked, starting = true)

    @Test fun startingLight() = snap("hoover_row_starting_light", false, docked, starting = true)

    @Test fun stuckDark() = snap("hoover_row_stuck_dark", true, stuck)

    @Test fun stuckLight() = snap("hoover_row_stuck_light", false, stuck)

    @Test fun cantReachDark() = snap("hoover_row_cant_reach_dark", true, cantReach)

    @Test fun cantReachLight() = snap("hoover_row_cant_reach_light", false, cantReach)

    @Test fun tokenRefusedDark() = snap("hoover_row_token_refused_dark", true, tokenRefused)

    @Test fun tokenRefusedLight() = snap("hoover_row_token_refused_light", false, tokenRefused)

    @Test fun notStartedDark() = snap("hoover_row_not_started_dark", true, docked, line = notStarted)

    @Test fun notStartedLight() = snap("hoover_row_not_started_light", false, docked, line = notStarted)
}
