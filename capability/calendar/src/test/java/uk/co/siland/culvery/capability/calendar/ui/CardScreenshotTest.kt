package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Card sizes as the Home grid gives them on the 1280×800 canvas. */
private val TALL_W = 397.dp
private val TALL_H = 572.dp
private val WIDE_W = 705.dp
private val WIDE_H = 279.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, width: Dp, height: Dp, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp)) {
                        Box(Modifier.size(width, height)) { content() }
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun todayDark() = snap("today_dark", true, TALL_W, TALL_H) { TodayCard(SampleUi.today) }
    @Test fun todayLight() = snap("today_light", false, TALL_W, TALL_H) { TodayCard(SampleUi.today) }
    @Test fun todayEmptyDark() = snap("today_empty_dark", true, TALL_W, TALL_H) { TodayCard(emptyList()) }
    @Test fun comingUpDark() = snap("coming_up_dark", true, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUp) }
    @Test fun comingUpLight() = snap("coming_up_light", false, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUp) }
    @Test fun comingUpBusyDark() = snap("coming_up_busy_dark", true, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUpBusy) }
    @Test fun connectDark() = snap("connect_dark", true, TALL_W, TALL_H) { ConnectCalendarCard() }
    @Test fun connectLight() = snap("connect_light", false, TALL_W, TALL_H) { ConnectCalendarCard() }
}
