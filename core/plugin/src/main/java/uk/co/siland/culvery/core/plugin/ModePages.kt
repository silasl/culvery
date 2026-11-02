package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A built-in mode's own parts of its page in Settings › Modes (15b §4.2): Holiday mode's. */
interface BuiltInModePage {
    val modeId: String

    /** "Turn on now" or "Turn off now", running the mode's own flow (Holiday's sheet asks the day you're back). */
    @Composable
    fun TurnButton()

    /** What sits between the switches and Plugs: Holiday's Lights while away and its fixed lines. */
    @Composable
    fun Sections()
}

/** Holiday lights' choice of presence automations, drawn on Holiday mode's page (15b decision 5). */
interface HolidayLightsSection {
    @Composable
    fun Content(modifier: Modifier)
}
