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
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room

/** The Home card in a REGULAR cell as the grid gives it on the 1280×800 canvas (the forecast's 345 × 279). */
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
        Box(Modifier.size(345.dp, 279.dp)) { LightsCard(main, holidayOn, run, status, line, NO_MAIN_ROOM_ACTIONS, NO_HOUSE_ACTIONS) }
    }

    @Test fun onDark() = snap("lights_card_on_dark", true)

    @Test fun onLight() = snap("lights_card_on_light", false)

    @Test fun offDark() = snap("lights_card_off_dark", true, main = room("Lounge"))

    @Test fun holidayDark() = snap("lights_card_holiday_dark", true, holidayOn = true)

    @Test fun turningOnDark() = snap("lights_card_turning_on_dark", true, run = HouseRun.TURNING_ON)

    @Test fun problemDark() = snap("lights_card_problem_dark", true, status = problemLine(LightsProblem.UNREACHABLE))
}
