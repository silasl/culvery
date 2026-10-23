package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.RELAX
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.ui.test.snap

/** The Home card in the right column's top half on the 1280×800 canvas; app's HomeGridTest pins the same size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class LightsCardScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val lounge = room("Lounge", on = true, brightness = 70)

    private fun snap(
        name: String,
        dark: Boolean,
        main: LightRoom? = lounge,
        holidayOn: Boolean = false,
        run: HouseRun? = null,
        status: String? = null,
        line: String? = null,
    ) = compose.snap(name, dark, Modifier.padding(16.dp)) {
        Box(Modifier.size(701.dp, 285.5.dp)) { LightsCard(main, holidayOn, main?.on == true, run, status, line, NO_MAIN_ROOM_ACTIONS, NO_HOUSE_ACTIONS) }
    }

    /** The card in the 343 × 212 half of the row beneath a utility card (10 §4.9); app's HomeGridTest pins the size. */
    private fun snapRow(name: String, dark: Boolean, line: String? = null) = compose.snap(name, dark, Modifier.padding(16.dp)) {
        Box(Modifier.size(343.dp, 212.dp)) { LightsCard(lounge, false, true, null, null, line, NO_MAIN_ROOM_ACTIONS, NO_HOUSE_ACTIONS) }
    }

    @Test fun onDark() = snap("lights_card_on_dark", true)

    @Test fun onLight() = snap("lights_card_on_light", false)

    @Test fun offDark() = snap("lights_card_off_dark", true, main = room("Lounge"))

    @Test fun sceneDark() = snap("lights_card_scene_dark", true, main = room("Lounge", on = true, brightness = 70, scenes = listOf(RELAX), active = RELAX.id))

    @Test fun holidayDark() = snap("lights_card_holiday_dark", true, holidayOn = true)

    @Test fun turningOnDark() = snap("lights_card_turning_on_dark", true, run = HouseRun.TURNING_ON)

    @Test fun problemDark() = snap("lights_card_problem_dark", true, status = problemLine(LightsProblem.UNREACHABLE))

    @Test fun rowDark() = snapRow("lights_card_row_dark", true)

    @Test fun rowLight() = snapRow("lights_card_row_light", false)

    /** A line from the last command under the buttons, in the row's height. */
    @Test fun rowLineDark() = snapRow("lights_card_row_line_dark", true, "2 rooms didn’t turn off. Try again.")

    @Test fun rowLineLight() = snapRow("lights_card_row_line_light", false, "2 rooms didn’t turn off. Try again.")
}
