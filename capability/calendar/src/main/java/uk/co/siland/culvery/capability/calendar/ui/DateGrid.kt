package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.dotColours
import uk.co.siland.culvery.capability.calendar.peopleByDay
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.HhDateGrid
import uk.co.siland.culvery.core.ui.monthCells

/** The people from [held] on each day [month]'s grid shows, ready for [DateGrid]'s dots. */
@Composable
internal fun rememberGridDots(held: Held?, month: YearMonth, household: EditorPeople, zone: ZoneId): Map<LocalDate, List<Person>> =
    remember(held, month, household, zone) {
        val cells = monthCells(month)
        held?.peopleByDay(cells.first(), cells.last(), household.order, zone).orEmpty()
            .mapValues { (_, ids) -> ids.mapNotNull { household.byId[it] } }
    }

/** [HhDateGrid] with a dot in each person's colour on the days they are on (up to three). */
@Composable
internal fun DateGrid(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    today: LocalDate,
    chosen: Set<LocalDate>,
    band: ClosedRange<LocalDate>?,
    allowed: (LocalDate) -> Boolean,
    dots: Map<LocalDate, List<Person>>,
    spoken: (LocalDate) -> String,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = remember(dots) { dots.mapValues { (_, people) -> dotColours(people).map(::Color) } }
    HhDateGrid(month, onMonth, today, chosen, band, allowed, colours, spoken, onPick, modifier)
}
