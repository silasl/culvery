package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.reminders.CardUi
import uk.co.siland.culvery.capability.reminders.DueKey
import uk.co.siland.culvery.capability.reminders.RemindersEngine
import uk.co.siland.culvery.capability.reminders.RemindersView

/** The card on the alert layer, as [engine] has it and [view] draws it. */
@Composable
fun ReminderCardHost(view: RemindersView, engine: RemindersEngine) {
    val showing by engine.showing.collectAsStateWithLifecycle()
    val card by view.card.collectAsStateWithLifecycle(initialValue = null)
    ReminderCardLayer(showing?.due?.key, card, onDone = engine::done, onLater = engine::later)
}

/**
 * Draws [card] when it is the engine's [engineKey], else a bare card for that key, so the layer is never a scrim with no
 * buttons. Each button answers the key its card was drawn with, not whichever card is up when the tap lands.
 */
@Composable
internal fun ReminderCardLayer(engineKey: DueKey?, card: CardUi?, onDone: (DueKey) -> Unit, onLater: (DueKey) -> Unit) {
    val key = engineKey ?: return
    val drawn = card?.takeIf { it.key == key } ?: CardUi(key, REMINDER, emptyList(), who = "", dueAt = "")
    ReminderCard(drawn, onDone = { onDone(key) }, onLater = { onLater(key) })
}
