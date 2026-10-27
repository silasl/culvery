package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** September 2026 on Wednesday 23rd: a range from the 25th to the 29th, with the 25th chosen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateGridScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.testTag("shot").background(Culvery.colors.surf).padding(16.dp).width(420.dp)) {
                    HhDateGrid(
                        month = YearMonth.of(2026, 9),
                        onMonth = {},
                        today = LocalDate.of(2026, 9, 23),
                        chosen = setOf(LocalDate.of(2026, 9, 25)),
                        band = LocalDate.of(2026, 9, 25)..LocalDate.of(2026, 9, 29),
                        allowed = { true },
                        dots = emptyMap(),
                        spoken = { "" },
                        onPick = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun dateGridDark() = snap("date_grid_dark", true)
    @Test fun dateGridLight() = snap("date_grid_light", false)
}
