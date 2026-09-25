package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tab content area: 1280 − 108 rail − 2 × 28 padding wide; 800 − 30 status − 24 − 22 high. */
private val CONTENT_W = 1116.dp
private val CONTENT_H = 724.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val now = SampleUi.NOW
    private val addNothing: (LocalDate) -> Unit = {}

    private fun snap(name: String, dark: Boolean, sync: SyncStatusUi, week: WeekUi = SampleUi.week, canAdd: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).size(CONTENT_W, CONTENT_H)) {
                        WeekView(WeekViewState(week, SampleUi.TODAY, sync, now), onAdd = addNothing.takeIf { canAdd })
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private val fresh = SyncStatusUi(now - 2 * 60_000, emptyList(), listOf("Google"), failingBeforeFirstSync = false)
    private val stale = fresh.copy(lastSyncMillis = now - 45 * 60_000)
    private val needsSignIn = fresh.copy(needsSignIn = listOf("Google"))

    @Test fun weekDark() = snap("week_dark", true, fresh, canAdd = true)
    @Test fun weekLight() = snap("week_light", false, fresh, canAdd = true)
    @Test fun weekStaleDark() = snap("week_stale_dark", true, stale)
    @Test fun weekNeedsSignInDark() = snap("week_needs_sign_in_dark", true, needsSignIn)
    @Test fun weekSyncingDark() = snap("week_syncing_dark", true, fresh, SampleUi.weekWithSyncing)
}
