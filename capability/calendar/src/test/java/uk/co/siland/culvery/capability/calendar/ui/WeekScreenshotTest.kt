package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.SyncProblem
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** The tab content area: 1280 − 108 rail − 2 × 28 padding wide; 800 − 30 status − 24 − 22 high. */
private val CONTENT_W = 1116.dp
private val CONTENT_H = 724.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val now = SampleUi.NOW
    private val addNothing: (LocalDate) -> Unit = {}

    private fun snap(name: String, dark: Boolean, sync: SyncStatusUi, week: WeekUi = SampleUi.week,
        canAdd: Boolean = false,
        weeksAhead: Int = 0,
        fontScale: Float = 1f,
        whileHolding: (() -> Unit)? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    WithFontScale(fontScale) {
                        Box(Modifier.testTag("shot").background(Culvery.colors.bg).size(CONTENT_W, CONTENT_H)) {
                            WeekView(WeekViewState(week, SampleUi.TODAY, sync, now, weeksAhead), onAdd = addNothing.takeIf { canAdd })
                        }
                    }
                }
            }
        }
        whileHolding?.invoke()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private val fresh = SyncStatusUi(now - 2 * 60_000, emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false)
    private val stale = fresh.copy(lastSyncMillis = now - 45 * 60_000)
    private val needsSignIn = fresh.copy(needsSignIn = listOf("Google Calendar"), problem = SyncProblem.SignIn("Google Calendar"))

    @Test fun weekFontScale15() = snap("week_fs15", false, fresh, canAdd = true, fontScale = 1.5f)
    @Test fun weekDark() = snap("week_dark", true, fresh, canAdd = true)
    /** A finger held below Sunday's last chip: the dashed ghost (9 §4.7). */
    @Test fun weekGhostDark() = snap("week_ghost_dark", true, fresh, canAdd = true) {
        val sunday = SampleUi.TODAY.plusDays(6)
        val column = compose.onNodeWithTag("week_day_$sunday").fetchSemanticsNode().boundsInRoot
        val chipBottom = compose.onNodeWithText("Sunday lunch at Gran's").fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { down(Offset(column.width / 2f, (chipBottom + column.bottom) / 2f - column.top)) }
        compose.waitForIdle()
    }
    @Test fun weekLight() = snap("week_light", false, fresh, canAdd = true)
    @Test fun weekStaleDark() = snap("week_stale_dark", true, stale)
    @Test fun weekStaleLight() = snap("week_stale_light", false, stale)
    @Test fun weekOfflineDark() = snap("week_offline_dark", true, stale.copy(problem = SyncProblem.Offline))
    @Test fun weekNeedsSignInDark() = snap("week_needs_sign_in_dark", true, needsSignIn)
    /** 5 §A.3: at the largest font scale the sign-in line still reaches "Reconnect in Settings › Calendars." (text is measured for real only in this graphics mode). */
    @Test fun weekNeedsSignInFontScale15() {
        snap("week_needs_sign_in_fs15", true, needsSignIn, canAdd = true, fontScale = 1.5f)
        val layout = mutableListOf<TextLayoutResult>()
        compose.runOnUiThread { compose.onNodeWithTag("week_subtitle").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout) }
        // The line sits on one row at its own width, so width overflow reads true against the looser constraint: check nothing is cut instead.
        assertThat(layout.single().isLineEllipsized(layout.single().lineCount - 1)).isFalse()
        assertThat(layout.single().didOverflowHeight).isFalse()
    }
    @Test fun weekSyncingDark() = snap("week_syncing_dark", true, fresh, SampleUi.weekWithSyncing)
    @Test fun weekNextLight() = snap("week_next_light", false, fresh, SampleUi.weekAhead(1), canAdd = true, weeksAhead = 1)
    @Test fun weekNextDark() = snap("week_next_dark", true, fresh, SampleUi.weekAhead(1), canAdd = true, weeksAhead = 1)
    @Test fun weekFurthestLight() = snap("week_furthest_light", false, fresh, SampleUi.weekAhead(3), canAdd = true, weeksAhead = 3)
    @Test fun weekFurthestDark() = snap("week_furthest_dark", true, fresh, SampleUi.weekAhead(3), canAdd = true, weeksAhead = 3)
}
