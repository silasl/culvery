package uk.co.siland.culvery.capability.calendar.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.laneRows
import uk.co.siland.culvery.capability.calendar.weekSpans
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** The week's lane of multi-day bars (9 §2.1, note 31), on the week from Mon 19 Oct 2026; real graphics for its pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp")
class WeekLaneTest {
    @get:Rule val compose = createComposeRule()

    private val monday: LocalDate = LocalDate.of(2026, 10, 19)
    private fun day(i: Int): LocalDate = monday.plusDays(i.toLong())
    private val sync = SyncStatusUi(SampleUi.NOW - 120_000, emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false)
    private lateinit var window: View

    private fun chipTitle(i: Int) = "Walk ${day(i).format(WEEKDAY)}"

    /** Half term Mon–Thu, a School trip Wed–Fri over it, Gran visiting from Fri past the week to Mon 26; one chip a day. */
    private fun week(spans: Boolean = true): WeekUi {
        val days = (0..6).map { i ->
            val slices = if (!spans) {
                emptyList()
            } else {
                listOfNotNull(
                    SampleUi.spanning("Half term", SampleUi.family, i + 1, 4).takeIf { i <= 3 },
                    SampleUi.spanning("School trip", SampleUi.mia, i - 1, 3).takeIf { i in 2..4 },
                    SampleUi.spanning("Gran visiting", SampleUi.sam, i - 3, 4).takeIf { i >= 4 },
                )
            }
            val bins = listOfNotNull(SampleUi.allDay("Bin day", SampleUi.family).takeIf { i == 1 })
            DayUi(day(i), slices + bins + SampleUi.event(chipTitle(i), "09:00–10:00", SampleUi.alex))
        }
        return WeekUi(monday, days, SampleUi.people, spans = laneRows(weekSpans(days)))
    }

    private fun show(week: WeekUi = week(), fontScale: Float = 1f, onOpen: (EventRef) -> Unit = {}, onAdd: ((LocalDate) -> Unit)? = null) =
        compose.setContent {
            window = LocalView.current.rootView
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = true) {
                    WithFontScale(fontScale) {
                        Box(Modifier.background(Culvery.colors.bg).size(1116.dp, 690.dp)) {
                            WeekView(WeekViewState(week, monday, sync, SampleUi.NOW), onOpen = onOpen, onAdd = onAdd)
                        }
                    }
                }
            }
        }

    private fun bar(title: String): Rect = compose.onNodeWithTag("week_span_${ref(title).listKey}").fetchSemanticsNode().boundsInRoot
    private fun ref(title: String) = EventRef("sample", "family", title)
    private fun column(i: Int): Rect = compose.onNodeWithTag("week_day_${day(i)}").fetchSemanticsNode().boundsInRoot
    private fun header(i: Int): Rect =
        compose.onNodeWithContentDescription(dayHeading(day(i), if (i == 0) "Today" else null)).fetchSemanticsNode().boundsInRoot
    /** Tuesday's first chip is Bin day; every other day's is its walk. */
    private fun firstChip(i: Int): Rect = chip(if (i == 1) "Bin day" else chipTitle(i))

    /** A chip's own bounds: the one node whose spoken name starts with [title] and isn't a bar's. */
    private fun chip(title: String): Rect = compose.onNode(
        SemanticsMatcher("$title's chip") {
            it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { d -> d.startsWith("$title, ") && " to " !in d }
        },
    ).fetchSemanticsNode().boundsInRoot
    private fun px(dp: Dp): Float = with(compose.density) { dp.toPx() }

    /** The chip list starts this far below a header's node: the header's own bottom inset, then the gap. */
    private val headerToChips = CalendarDimens.columnHeaderInset + CalendarDimens.columnGap

    @Test
    fun aSpanIsOneBarAcrossItsDays() {
        show()
        val half = bar("Half term")
        assertThat(half.left).isWithin(px(1.dp)).of(column(0).left + px(7.dp))
        assertThat(half.right).isWithin(px(1.dp)).of(column(3).right - px(7.dp))
        compose.onAllNodesWithText("Half term", useUnmergedTree = true).assertCountEquals(1)
        val isHalfTermChip = SemanticsMatcher("a Half term chip") {
            it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { d -> d.startsWith("Half term, Family, ") && " to " !in d }
        }
        compose.onAllNodes(isHalfTermChip).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aBarRunningPastTheWeekSaysUntil() {
        show()
        compose.onNodeWithText("until Mon →", useUnmergedTree = true).assertExists()
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
        val gran = compose.onNodeWithTag("week_span_${ref("Gran visiting").listKey}").fetchSemanticsNode().boundsInWindow
        val fill = bitmap.getPixel((gran.right - 1).toInt(), gran.center.y.toInt())
        // The square right end fills its corner; the rounded left end leaves its corner unfilled.
        assertThat(bitmap.getPixel((gran.right - 1).toInt(), (gran.top + 1).toInt())).isEqualTo(fill)
        assertThat(bitmap.getPixel((gran.left + 1).toInt(), (gran.top + 1).toInt())).isNotEqualTo(fill)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theColumnsSpacerMatchesTheLane() {
        show()
        val laneBottom = bar("School trip").bottom
        assertThat(bar("Gran visiting").top).isWithin(px(1.dp)).of(bar("Half term").top)
        assertThat(laneBottom - bar("Half term").top).isWithin(px(1.dp)).of(px(laneHeight(2)))
        (0..6).forEach { assertThat(firstChip(it).top).isWithin(px(1.dp)).of(laneBottom + px(CalendarDimens.columnGap)) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noSpansMeansNoLaneAndNoSpacer() {
        show(week(spans = false))
        val isBar = SemanticsMatcher("a lane bar") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("week_span_") == true }
        compose.onAllNodes(isBar).assertCountEquals(0)
        (0..6).forEach {
            assertThat(firstChip(it).top).isWithin(px(1.dp)).of(header(it).bottom + px(headerToChips))
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theLaneSitsUnderTheHeadersAtLargeFont() {
        show(fontScale = 1.3f)
        assertThat(bar("Half term").top).isWithin(px(1.dp)).of(header(0).bottom + px(headerToChips))
        assertThat(bar("Gran visiting").top).isWithin(px(1.dp)).of(header(5).bottom + px(headerToChips))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aBarOpensItsEvent() {
        val opened = mutableListOf<EventRef>()
        show(onOpen = { opened += it })
        compose.onNodeWithTag("week_span_${ref("Half term").listKey}")
            .assertContentDescriptionEquals("Half term, Family, Monday 19 October to Thursday 22 October")
            .performClick()
        assertThat(opened).containsExactly(ref("Half term"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun singleDayAllDayStaysAChip() {
        show()
        val bin = chip("Bin day")
        assertThat(bin.left).isWithin(px(1.dp)).of(column(1).left + px(CalendarDimens.weekColumnPaddingSide))
        assertThat(bin.top).isAtLeast(bar("Half term").bottom)
        val isBar = SemanticsMatcher("Bin day's bar") { it.config.getOrNull(SemanticsProperties.TestTag) == "week_span_${ref("Bin day").listKey}" }
        compose.onAllNodes(isBar).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTapUnderTheLaneInAnEmptyColumnStillAdds() {
        val added = mutableListOf<LocalDate>()
        // Sunday without its chip: only Gran visiting's bar in its first lane row, so the second row's place is empty.
        val w = week().let { w -> w.copy(days = w.days.map { d -> if (d.date == day(6)) d.copy(events = d.events.filter { it.span != null }) else d }) }
        show(w, onAdd = { added += it })
        val sunday = column(6)
        val y = bar("Gran visiting").bottom + px(CalendarDimens.laneRowGap) + px(CalendarDimens.laneRow) / 2
        compose.onNodeWithTag("week_day_${day(6)}").performTouchInput { click(Offset(sunday.width / 2f, y - sunday.top)) }
        assertThat(added).containsExactly(day(6))
        val node = compose.onNodeWithTag("week_add_${day(6)}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertThat(node.top).isWithin(px(1.dp)).of(bar("School trip").bottom + px(CalendarDimens.columnGap))
        compose.assertClickablesHaveRoleAndLabel()
    }
}
