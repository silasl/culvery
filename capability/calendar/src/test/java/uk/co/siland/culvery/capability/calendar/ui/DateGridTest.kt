package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.spokenWho
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** The shared month grid on its own, in the dark theme, at 11:54 on Wednesday 23 September 2026. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateGridTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private val today = SampleUi.TODAY
    private var month by mutableStateOf(YearMonth.from(today))
    private var picked by mutableStateOf<LocalDate?>(null)
    private var onDay by mutableStateOf<Map<LocalDate, List<Person>>>(emptyMap())
    private var allowed: (LocalDate) -> Boolean = { true }
    private val taps = mutableListOf<LocalDate>()

    private fun show(scale: Float = 1f) {
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) {
                    Box(Modifier.height(GRID_HEIGHT).background(c.surf).verticalScroll(rememberScrollState())) {
                        DateGrid(
                            month = month,
                            onMonth = { month = it },
                            today = today,
                            chosen = setOfNotNull(picked),
                            band = null,
                            allowed = { allowed(it) },
                            dots = onDay,
                            spoken = { spokenWho(onDay[it].orEmpty()) },
                            onPick = { taps += it; picked = it },
                        )
                    }
                }
            }
        }
    }

    private fun cell(date: LocalDate) = compose.onNodeWithTag("day_$date")

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun fadeOf(date: LocalDate): Float = cell(date).fetchSemanticsNode().config[DayFade]

    private fun height(tag: String) = part(tag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun width(tag: String) = part(tag).getUnclippedBoundsInRoot().let { it.right - it.left }

    /** The circle's top centre, 3 dp in: its fill, clear of the date and the dots. */
    private fun circleFill(date: LocalDate): Int = pixelAt(part("day_${date}_circle"), (width("day_${date}_circle").value / 2).dp, 3.dp)

    private fun at(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    @Test
    fun aDateThatIsNotAllowedIsDimmedAndCannotBeTapped() {
        allowed = { it >= at(9, 25) }
        show()
        listOf(at(9, 24), today).forEach { d ->
            assertThat(fadeOf(d)).isEqualTo(HhAlpha.PAST_DAY)
            cell(d).assertIsNotEnabled()
            cell(d).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
            cell(d).performClick()
        }
        assertThat(taps).isEmpty()
        assertThat(fadeOf(at(9, 25))).isEqualTo(1f)
        cell(at(9, 25)).assertIsEnabled().performClick()
        assertThat(taps).containsExactly(at(9, 25))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theMonthStartsOnMonday() {
        val cells = monthCells(YearMonth.of(2026, 9))
        assertThat(cells.first()).isEqualTo(at(8, 31))
        assertThat(cells.last()).isEqualTo(at(10, 4))
        assertThat(cells).hasSize(35)
        assertThat(cells.first().dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(monthCells(YearMonth.of(2026, 8))).hasSize(42)
        show()
        compose.onNodeWithTag("month_title").assertTextEquals("September 2026")
        val letters = part("month_letters").fetchSemanticsNode().children.flatMap { it.config[SemanticsProperties.Text] }.map { it.text }
        assertThat(letters).containsExactly("M", "T", "W", "T", "F", "S", "S").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun pastAndOutsideDaysFade() {
        show()
        assertThat(fadeOf(at(9, 21))).isEqualTo(HhAlpha.PAST_DAY)
        assertThat(fadeOf(at(8, 31))).isEqualTo(0.58f)
        assertThat(fadeOf(at(10, 1))).isEqualTo(0.58f)
        assertThat(fadeOf(today)).isEqualTo(1f)
        assertThat(fadeOf(at(9, 30))).isEqualTo(1f)
        // Any date is allowed, the past included.
        cell(at(9, 21)).performClick()
        compose.runOnIdle { assertThat(picked).isEqualTo(at(9, 21)) }
        assertThat(fadeOf(at(9, 21))).isEqualTo(1f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun todayIsRingedAndTheChosenDayFilled() {
        show()
        val ring = pixelAt(part("day_${today}_circle"), 1.dp, (height("day_${today}_circle").value / 2).dp)
        assertWithMessage("today's ring").that(ring).isEqualTo(c.accent.toArgb())
        assertThat(circleFill(today)).isEqualTo(c.surf.toArgb())
        assertThat(part("day_${today}_date").textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(part("day_${at(9, 24)}_date").textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W500)
        val sat = at(9, 26)
        cell(sat).performClick()
        assertWithMessage("the chosen day's fill").that(circleFill(sat)).isEqualTo(c.accent.toArgb())
        assertThat(part("day_${sat}_date").textLayout().layoutInput.style.color).isEqualTo(c.accentInk)
        assertThat(part("day_${sat}_date").textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(cell(sat).fetchSemanticsNode().config[SemanticsProperties.Selected]).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun upToThreeDotsFromHeldEvents() {
        onDay = mapOf(
            at(9, 25) to listOf(SampleUi.alex, SampleUi.sam, SampleUi.mia, SampleUi.family),
            at(9, 26) to listOf(SampleUi.mia),
        )
        show()
        val dots = compose.onAllNodesWithTag("day_${at(9, 25)}_dot", useUnmergedTree = true)
        dots.assertCountEquals(3)
        val colours = (0 until 3).map { i ->
            val dot = dots[i]
            val b = dot.getUnclippedBoundsInRoot()
            assertThat((b.right - b.left).value).isWithin(0.5f).of(5f)
            assertThat((b.bottom - b.top).value).isWithin(0.5f).of(5f)
            pixelAt(dot, 2.5.dp, 2.5.dp)
        }
        assertThat(colours).containsExactly(
            Color(SampleUi.alex.color).toArgb(),
            Color(SampleUi.sam.color).toArgb(),
            Color(SampleUi.mia.color).toArgb(),
        ).inOrder()
        compose.onAllNodesWithTag("day_${at(9, 26)}_dot", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("day_${at(9, 29)}_dot", useUnmergedTree = true).assertCountEquals(0)
        // TalkBack names everyone on the day, the fourth too.
        cell(at(9, 25)).assertContentDescriptionEquals("Friday 25 September, Alex, Sam, Mia and Family")
        cell(at(9, 26)).assertContentDescriptionEquals("Saturday 26 September, Mia")
        cell(at(9, 29)).assertContentDescriptionEquals("Tuesday 29 September")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theMonthPagesBothWays() {
        show()
        compose.onNodeWithTag("month_next").assertContentDescriptionEquals("Later months").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("October 2026")
        cell(at(10, 31)).assertExists()
        compose.onNodeWithTag("month_prev").assertContentDescriptionEquals("Earlier months").performClick()
        compose.onNodeWithTag("month_prev").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("August 2026")
        cell(at(8, 3)).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun atFontScale13TheLastWeekCanStillBeReached() {
        month = YearMonth.of(2026, 8)
        show(scale = 1.3f)
        cell(at(9, 6)).performScrollTo().performClick()
        compose.runOnIdle { assertThat(picked).isEqualTo(at(9, 6)) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    private companion object {
        val GRID_HEIGHT = 400.dp
    }
}
