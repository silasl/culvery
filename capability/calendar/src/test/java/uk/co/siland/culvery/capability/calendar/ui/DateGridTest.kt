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
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** The calendar's grid: people on a day become dots and TalkBack names them. The grid itself is tested in core:ui. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateGridTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val today = SampleUi.TODAY
    private var onDay by mutableStateOf<Map<LocalDate, List<Person>>>(emptyMap())

    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.height(400.dp).background(DarkColors.surf).verticalScroll(rememberScrollState())) {
                    DateGrid(
                        month = YearMonth.from(today),
                        onMonth = {},
                        today = today,
                        chosen = emptySet(),
                        band = null,
                        allowed = { true },
                        dots = onDay,
                        spoken = { spokenWho(onDay[it].orEmpty()) },
                        onPick = {},
                    )
                }
            }
        }
    }

    private fun cell(date: LocalDate) = compose.onNodeWithTag("day_$date")

    private fun at(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

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
}
