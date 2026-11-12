package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import kotlinx.coroutines.flow.StateFlow

/** The seasonal themes, in the order that settles overlapping dates: the first one whose dates hold today wins (14 §4.1). */
enum class SeasonalTheme(val label: String, val defaultDates: SeasonDates) {
    HALLOWEEN("Halloween", SeasonDates(MonthDay.of(10, 24), MonthDay.of(10, 31))),
    CHRISTMAS("Christmas", SeasonDates(MonthDay.of(12, 1), MonthDay.of(1, 6))),
}

/** A theme's only description; a new theme must name its look here (14 §3). */
val SeasonalTheme.look: SeasonalLook
    get() = when (this) {
        SeasonalTheme.HALLOWEEN -> HalloweenLook
        SeasonalTheme.CHRISTMAS -> ChristmasLook
    }

/** The days a theme shows, both ends included; an end before the start runs over the new year (14 §4.1). */
data class SeasonDates(val start: MonthDay, val end: MonthDay) {
    operator fun contains(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        return if (end < start) day >= start || day <= end else day >= start && day <= end
    }
}

/** The day a range starting on this day starts in [year]: a 29 February start is 1 March in a year without one (21 §4.8). */
fun MonthDay.firstDayIn(year: Int): LocalDate =
    if (this == LEAP_DAY && !Year.isLeap(year.toLong())) LocalDate.of(year, 3, 1) else atYear(year)

private val LEAP_DAY: MonthDay = MonthDay.of(2, 29)

fun defaultSeasonDates(): Map<SeasonalTheme, SeasonDates> = SeasonalTheme.entries.associateWith { it.defaultDates }

/** What a theme changes (14 §3): its colours, and each optional touch; null leaves the normal look. */
class SeasonalLook(
    val night: HhColors,
    val day: HhColors,
    /** The big Home clock's digits, in `accent`, each in a fixed slot. */
    val clockFace: TextStyle? = null,
    /** Drawn in the rail's mark spot, [width] wide; a picture only. */
    val mark: (@Composable (width: Dp) -> Unit)? = null,
    /** Drawn behind the Today card's content, over its surface, inside its clip. */
    val cardCorner: (DrawScope.(HhColors) -> Unit)? = null,
    /** Drawn over the Today card, outside its clip, so it can hang over the card's edge; a picture only. */
    val cardPin: (DrawScope.(HhColors) -> Unit)? = null,
    /** Drawn behind Home's content; moves only while [drifting]. */
    val sky: (@Composable (drifting: Boolean, modifier: Modifier) -> Unit)? = null,
)

interface ShowingTheme {
    /** Null while no theme shows. */
    val showing: StateFlow<SeasonalTheme?>

    /** Whether the wall wears a birthday, preview included; it never changes [showing] (18 §4.3). */
    val birthdayShowing: StateFlow<Boolean>
}

fun colorsOf(look: SeasonalLook?, dark: Boolean): HhColors = when {
    look == null -> if (dark) DarkColors else LightColors
    dark -> look.night
    else -> look.day
}

fun colorsFor(seasonal: SeasonalTheme?, dark: Boolean): HhColors = colorsOf(seasonal?.look, dark)

/** What Home's header measures, in pixels; Home sets it and the sky reads it when it draws, so a change redraws only the sky. */
class HomeSkyBand {
    var headerWidth by mutableIntStateOf(0)
    var headerHeight by mutableIntStateOf(0)
    var clockWidth by mutableIntStateOf(0)
    var dateWidth by mutableIntStateOf(0)
    var itemsWidth by mutableIntStateOf(0)
    var greetingWidth by mutableIntStateOf(0)
}

val LocalHomeSkyBand = staticCompositionLocalOf<HomeSkyBand?> { null }
