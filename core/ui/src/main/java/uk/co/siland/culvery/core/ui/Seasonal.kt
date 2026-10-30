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
import kotlinx.coroutines.flow.StateFlow

/** The seasonal themes, in the order that settles overlapping dates: the first one whose dates hold today wins (14 §4.1). */
enum class SeasonalTheme(val label: String, val defaultDates: SeasonDates) {
    HALLOWEEN("Halloween", SeasonDates(MonthDay.of(10, 24), MonthDay.of(10, 31))),
}

/** A theme's only description; a new theme must name its look here (14 §3). */
val SeasonalTheme.look: SeasonalLook
    get() = when (this) {
        SeasonalTheme.HALLOWEEN -> HalloweenLook
    }

/** The days a theme shows, both ends included; an end before the start runs over the new year (14 §4.1). */
data class SeasonDates(val start: MonthDay, val end: MonthDay) {
    operator fun contains(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        return if (end < start) day >= start || day <= end else day >= start && day <= end
    }
}

fun defaultSeasonDates(): Map<SeasonalTheme, SeasonDates> = SeasonalTheme.entries.associateWith { it.defaultDates }

enum class SeasonalSound { HAUNTED_ORGAN }

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
    /** Drawn behind Home's content; moves only while [drifting]. */
    val sky: (@Composable (drifting: Boolean, modifier: Modifier) -> Unit)? = null,
    val sound: SeasonalSound? = null,
)

interface ShowingTheme {
    /** Null while no theme shows. */
    val showing: StateFlow<SeasonalTheme?>
}

val LocalSeasonalTheme = staticCompositionLocalOf<SeasonalTheme?> { null }

fun colorsFor(seasonal: SeasonalTheme?, dark: Boolean): HhColors = when {
    seasonal == null -> if (dark) DarkColors else LightColors
    dark -> seasonal.look.night
    else -> seasonal.look.day
}

/** What Home's header measures, in pixels; Home sets it and the sky reads it when it draws, so a change redraws only the sky. */
class HomeSkyBand {
    var headerWidth by mutableIntStateOf(0)
    var headerHeight by mutableIntStateOf(0)
    var clockWidth by mutableIntStateOf(0)
    var dateWidth by mutableIntStateOf(0)
    var itemsWidth by mutableIntStateOf(0)
}

val LocalHomeSkyBand = staticCompositionLocalOf<HomeSkyBand?> { null }
