package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.ui.test.snap

/** Every week on Mon and Wed from Monday 19 October 2026, stopping after 10 times. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepeatPickerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 19)

    private fun shot(name: String, dark: Boolean) {
        val pick = RepeatPick(RepeatChoice.Weekly).apply {
            toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY)
            stopsAfter()
        }
        compose.snap(name, dark, Modifier.padding(16.dp).width(640.dp)) {
            HhRepeatPicker(pick, monday, monday, ZoneId.of("Europe/London"))
        }
    }

    @Test fun repeatPickerDark() = shot("repeat_picker_dark", dark = true)

    @Test fun repeatPickerLight() = shot("repeat_picker_light", dark = false)
}
