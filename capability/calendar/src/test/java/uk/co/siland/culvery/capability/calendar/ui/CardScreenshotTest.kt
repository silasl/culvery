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
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** Card sizes as the Home grid gives them on the 1280×800 canvas. */
private val TALL_W = 397.dp
private val TALL_H = 572.dp
private val WIDE_W = 705.dp
private val WIDE_H = 279.dp
private val TODAY_W = todayCardSize.width
private val TODAY_H = todayCardSize.height

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, width: Dp, height: Dp, seasonal: SeasonalTheme? = null, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark, seasonal = seasonal) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp)) {
                        Box(Modifier.size(width, height)) { content() }
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun todayDark() = snap("today_dark", true, TODAY_W, TODAY_H) { TodayCard(SampleUi.today, onAdd = {}) }
    @Test fun todayLight() = snap("today_light", false, TODAY_W, TODAY_H) { TodayCard(SampleUi.today, onAdd = {}) }
    @Test fun todayHalloweenDark() = snap("today_halloween_dark", true, TODAY_W, TODAY_H, SeasonalTheme.HALLOWEEN) { TodayCard(SampleUi.today, onAdd = {}) }
    @Test fun todayHalloweenLight() = snap("today_halloween_light", false, TODAY_W, TODAY_H, SeasonalTheme.HALLOWEEN) { TodayCard(SampleUi.today, onAdd = {}) }
    @Test fun todayEmptyDark() = snap("today_empty_dark", true, TODAY_W, TODAY_H) { TodayCard(emptyList()) }
    @Test fun todaySharedDark() = snap("today_shared_dark", true, TODAY_W, TODAY_H) { TodayCard(SampleUi.shared, onAdd = {}, tomorrow = DayUi(SampleUi.TODAY.plusDays(1), SampleUi.shared)) }
    @Test fun todaySharedLight() = snap("today_shared_light", false, TODAY_W, TODAY_H) { TodayCard(SampleUi.shared, onAdd = {}, tomorrow = DayUi(SampleUi.TODAY.plusDays(1), SampleUi.shared)) }
    @Test fun comingUpSharedDark() = snap("coming_up_shared_dark", true, WIDE_W, WIDE_H) { ComingUpCard(listOf(DayUi(SampleUi.TODAY.plusDays(1), SampleUi.shared))) }

    @Test fun todayScrollDark() = snap("today_scroll_dark", true, TODAY_W, TODAY_H) { TodayCard(SampleUi.todayBusy, onAdd = {}) }
    @Test fun todayScrollLight() = snap("today_scroll_light", false, TODAY_W, TODAY_H) { TodayCard(SampleUi.todayBusy, onAdd = {}) }

    @Test
    fun todayToppedUpDark() = snap("today_topped_up_dark", true, TODAY_W, TODAY_H) {
        TodayCard(SampleUi.today.take(2), onAdd = {}, tomorrow = SampleUi.tomorrow)
    }

    @Test
    fun todayNothingTodayDark() = snap("today_nothing_today_dark", true, TODAY_W, TODAY_H) {
        TodayCard(emptyList(), onAdd = {}, tomorrow = SampleUi.tomorrow)
    }

    @Test fun todayFs15() = snap("today_fs15", true, TODAY_W, TODAY_H) { WithFontScale(1.5f) { TodayCard(SampleUi.today, onAdd = {}) } }
    @Test fun comingUpDark() = snap("coming_up_dark", true, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUp) }
    @Test fun comingUpLight() = snap("coming_up_light", false, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUp) }
    @Test fun comingUpBusyDark() = snap("coming_up_busy_dark", true, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUpBusy) }
    @Test fun connectDark() = snap("connect_dark", true, TALL_W, TALL_H) { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
    @Test fun connectLight() = snap("connect_light", false, TALL_W, TALL_H) { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
    @Test fun todayMultiDayDark() = snap("today_multiday_dark", true, TODAY_W, TODAY_H) { TodayCard(SampleUi.todayWithSpan, onAdd = {}) }
    @Test fun todayBadgesDark() = snap("today_badges_dark", true, TODAY_W, TODAY_H) { TodayCard(SampleUi.todayWithBadges) }
}
