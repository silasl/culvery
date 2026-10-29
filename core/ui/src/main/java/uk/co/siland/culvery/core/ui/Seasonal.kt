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
import androidx.compose.ui.unit.dp
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

object SeasonalDimens {
    /** How far the Today card's cobweb reaches from the card's top-right corner. */
    val webSpan = 88.dp
    val webStroke = 1.dp

    /** A bat's width, wing tip to wing tip; its height is half. */
    val batSpan = 26.dp

    /** The sky's room is Home's header, from this far right of the clock and date to this far left of the header items. */
    val skyGap = 24.dp

    /** The moon's size, and how far its edge sits inside the sky's top and right edges. */
    val moonRadius = 30.dp
    val moonEnd = 12.dp
    val moonTop = 8.dp

    /** The tops of the two lanes the bats fly in, from the sky's top. */
    val batLanes = listOf(14.dp, 84.dp)
}

/** How long a bat takes to cross the sky and come back in at the left. */
const val BAT_LAP_MS = 45_000L

/** The sky moves at most this often, about 15 frames a second. */
const val SKY_FRAME_MS = 66L

/** What Home's header measures, in pixels; Home sets it and the sky reads it when it draws, so a change redraws only the sky. */
class HomeSkyBand {
    var headerWidth by mutableIntStateOf(0)
    var headerHeight by mutableIntStateOf(0)
    var clockWidth by mutableIntStateOf(0)
    var dateWidth by mutableIntStateOf(0)
    var itemsWidth by mutableIntStateOf(0)
}

val LocalHomeSkyBand = staticCompositionLocalOf<HomeSkyBand?> { null }
