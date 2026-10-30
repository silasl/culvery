package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DisplayPrefs

/** Settings › Display as Settings shows it: in the page area beside the 320 dp page list. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h1000dp-land-hdpi")
class DisplaySettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 15)
        val NEXT = SeasonalTheme.HALLOWEEN to LocalDate.of(2027, 10, 24)
    }

    private val actions = DisplaySettingsActions({ _, _ -> }, {}, {}, {}, {}, {}, {})

    private val overlay = TestOverlay()

    private fun snap(
        name: String,
        dark: Boolean,
        camera: CameraState,
        previewing: SeasonalTheme? = null,
        next: Pair<SeasonalTheme, LocalDate>? = null,
        beforeShot: () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = dark, seasonal = previewing) {
                    Box(Modifier.size(960.dp, 1000.dp).background(Culvery.colors.bg)) {
                        InSettingsPane {
                            DisplaySettingsScreen(DisplaySettingsUi(DisplayPrefs(), camera, sunKnown = true, today = TODAY, previewing = previewing, next = next), actions)
                        }
                        Box(Modifier.align(Alignment.CenterEnd)) { overlay.content?.invoke() }
                    }
                }
            }
        }
        beforeShot()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun scrollToEnd() {
        compose.onNodeWithTag("display_next").performScrollTo()
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.7f) }
        compose.waitForIdle()
    }

    private fun datesSheet(name: String, dark: Boolean) = snap(name, dark, CameraState.ALLOWED) {
        compose.onNodeWithTag("display_dates_halloween").performScrollTo().performClick()
        compose.onNodeWithTag("day_2026-10-20").performClick()
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("day_2026-11-02").performClick()
        compose.waitForIdle()
    }

    @Test fun allowedDark() = snap("settings_display_allowed_dark", true, CameraState.ALLOWED)

    @Test fun allowedLight() = snap("settings_display_allowed_light", false, CameraState.ALLOWED)

    @Test fun notAskedDark() = snap("settings_display_not_asked_dark", true, CameraState.NOT_ASKED)

    @Test fun previewDark() = snap("settings_display_preview_dark", true, CameraState.ALLOWED, SeasonalTheme.HALLOWEEN)

    @Test fun previewLight() = snap("settings_display_preview_light", false, CameraState.ALLOWED, SeasonalTheme.HALLOWEEN)

    @Test fun datesSheetDark() = datesSheet("settings_display_dates_sheet_dark", true)

    @Test fun datesSheetLight() = datesSheet("settings_display_dates_sheet_light", false)

    @Test fun nextDark() = snap("settings_display_next_dark", true, CameraState.ALLOWED, next = NEXT, beforeShot = ::scrollToEnd)

    @Test fun nextLight() = snap("settings_display_next_light", false, CameraState.ALLOWED, next = NEXT, beforeShot = ::scrollToEnd)

    @Test fun refusedDark() = snap("settings_display_refused_dark", true, CameraState.REFUSED)
}
