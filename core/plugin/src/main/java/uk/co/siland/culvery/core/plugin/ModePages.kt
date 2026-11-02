package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A built-in mode's own parts of its page in Settings › Modes (15b §4.2): Holiday mode's. */
interface BuiltInModePage {
    val modeId: String

    /** "Turn on now" or "Turn off now" at the end of the page's head, running the mode's own flow (Holiday's sheet asks the day you're back). */
    @Composable
    fun TurnButton()

    /** What the turn button didn't do, under the page's head. */
    @Composable
    fun TurnLine()

    /** What sits between the switches and Plugs: Holiday's Lights while away and its fixed lines. */
    @Composable
    fun Sections()
}

/** A mode page's turn button's label while the mode is [on] or off. */
fun turnNowLabel(on: Boolean) = if (on) "Turn off now" else "Turn on now"

/** Holiday lights' choice of presence automations, drawn on Holiday mode's page (15b decision 5). */
interface HolidayLightsSection {
    @Composable
    fun Content(modifier: Modifier)
}
