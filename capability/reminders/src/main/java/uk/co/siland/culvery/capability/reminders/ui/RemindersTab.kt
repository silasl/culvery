package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.capability.reminders.BinsRowUi
import uk.co.siland.culvery.capability.reminders.CHANGE_THE_BINS_REMINDER
import uk.co.siland.culvery.capability.reminders.CANT_READ_BINS
import uk.co.siland.culvery.capability.reminders.DueKey
import uk.co.siland.culvery.capability.reminders.RowUi
import uk.co.siland.culvery.capability.reminders.TabUi
import uk.co.siland.culvery.capability.reminders.binsRowTitle
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhStripedBar
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

internal const val REMINDERS = "Reminders"
internal const val ADD_REMINDER = "Add reminder"
internal const val WAITING = "Waiting"
internal const val TODAY = "Today"
internal const val COMING_UP = "Coming up"
private const val SWITCH_ON = "On"
private const val SWITCH_OFF = "Off"
const val NO_REMINDERS = "No reminders yet. Tap Add reminder to make one."

/**
 * The Reminders tab (22 §4.1): Waiting with a Done on each row, Today, then Coming up, a group left out when it has no
 * rows, and the bins reminder's row last when [TabUi.bins] is given. Open to everyone; a tap on a row opens it.
 */
@Composable
fun RemindersTab(
    ui: TabUi,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onDone: (DueKey) -> Unit,
    onBins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = Modifier.padding(bottom = RemindersDimens.rowGap)
    val empty = ui.waiting.isEmpty() && ui.today.isEmpty() && ui.comingUp.isEmpty()
    LazyColumn(modifier = modifier.fillMaxSize().testTag("reminders_tab")) {
        item {
            HhPageHeader(REMINDERS, null) {
                HhButton(ADD_REMINDER, onAdd, Modifier.testTag("reminders_add"), tone = ButtonTone.Primary, icon = Icons.ADD, on = ButtonSurface.Bg)
            }
        }
        group(WAITING, "waiting", ui.waiting, gap, onOpen, onDone, first = true)
        group(TODAY, "today", ui.today, gap, onOpen, onDone, first = ui.waiting.isEmpty())
        group(COMING_UP, "coming_up", ui.comingUp, gap, onOpen, onDone, first = ui.waiting.isEmpty() && ui.today.isEmpty())
        if (empty) item { Text(NO_REMINDERS, style = HhType.body, color = Culvery.colors.mute, modifier = gap.testTag("reminders_empty")) }
        ui.bins?.let { bins -> item { BinsRow(bins, onBins, gap.padding(top = RemindersDimens.sectionTop)) } }
    }
}

private fun LazyListScope.group(
    label: String,
    name: String,
    rows: List<RowUi>,
    gap: Modifier,
    onOpen: (Long) -> Unit,
    onDone: (DueKey) -> Unit,
    first: Boolean,
) {
    if (rows.isEmpty()) return
    item(key = "label_$name") { HhSectionLabel(label, gap.padding(top = if (first) 0.dp else RemindersDimens.sectionTop).testTag("reminders_$name")) }
    itemsIndexed(rows, key = { _, row -> "${name}_${row.id ?: row.key}" }) { index, row ->
        ReminderRow(row, "reminders_${name}_$index", onOpen, onDone, gap)
    }
}

@Composable
private fun ReminderRow(row: RowUi, tag: String, onOpen: (Long) -> Unit, onDone: (DueKey) -> Unit, modifier: Modifier) {
    val c = Culvery.colors
    HhListRow(
        title = row.words,
        subtitle = listOfNotNull(row.who, row.note).joinToString(" · "),
        modifier = modifier.testTag(tag),
        leading = if (row.stripes.isEmpty()) null else {
            { HhStripedBar(row.stripes.map { it.toStripe() }, RemindersDimens.barWidth, Modifier.height(RemindersDimens.rowBarHeight)) }
        },
        trailing = {
            Text(row.time, style = HhType.rowTitle.copy(fontSize = RemindersDimens.timeSize), color = c.ink, maxLines = 1, overflow = TextOverflow.Clip)
            row.key?.let { key -> HhButton(DONE, { onDone(key) }, Modifier.testTag("${tag}_done"), tone = ButtonTone.Primary, on = ButtonSurface.Surf) }
        },
        onClick = row.id?.let { id -> { onOpen(id) } },
    )
}

@Composable
private fun BinsRow(bins: BinsRowUi, onBins: () -> Unit, modifier: Modifier) {
    HhListRow(
        title = binsRowTitle(bins.time),
        modifier = modifier.testTag("reminders_bins").semantics { stateDescription = if (bins.on) SWITCH_ON else SWITCH_OFF },
        trailing = { HhSwitch(bins.on, tag = "reminders_bins_switch") },
        onClick = onBins,
        clickLabel = CHANGE_THE_BINS_REMINDER,
        below = if (bins.cantRead) {
            { Text(CANT_READ_BINS, style = HhType.secondary, color = Culvery.colors.danger, modifier = Modifier.testTag("reminders_bins_cant_read")) }
        } else {
            null
        },
    )
}
