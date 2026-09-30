package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ReadOnlyReason
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPersonChip
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSheetButton

enum class DetailMode { Idle, ChoosingPerson, ConfirmingDelete }

/** The Calendar row's value: the source's name, marked read-only when nobody can change it. */
internal fun calendarLabel(event: EventUi): String =
    if (event.readOnlyReason == ReadOnlyReason.OtherCalendar) "${event.sourceName} · read-only" else event.sourceName

/**
 * Hand-off §7 "Sheet 1 — Event detail". Stateless: the host decides [mode] and [busy]. Buttons are never hidden
 * for permission reasons; the checks happen when they are tapped. Delete runs the guard ([onDelete]); only the
 * confirmation's "Delete event" deletes ([onConfirmDelete]). Edit ([onEdit]) shows exactly where Delete does and
 * opens the add/edit sheet without a PIN (2b-2 design D12).
 */
@Composable
fun EventDetailSheet(
    detail: EventDetailUi,
    people: List<Person>,
    mode: DetailMode,
    busy: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    onConfirmDelete: () -> Unit,
    onChoosePerson: () -> Unit,
    onAssign: (Person) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val e = detail.event
    HhSheet(
        padding = PaddingValues(
            start = CalendarDimens.sheetPaddingH,
            end = CalendarDimens.sheetPaddingH,
            top = CalendarDimens.sheetPaddingTop,
            bottom = CalendarDimens.sheetPaddingBottom,
        ),
        modifier = modifier.testTag("detail_sheet"),
    ) {
        Header(e, onClose)
        InfoCard(detail)
        when {
            e.readOnlyReason == ReadOnlyReason.OtherCalendar || e.readOnlyReason == ReadOnlyReason.NotMaster -> {
                val subscribed = e.readOnlyReason == ReadOnlyReason.OtherCalendar
                Note(
                    icon = "lock",
                    title = if (subscribed) "From ${e.sourceName} (read-only)" else "From ${e.sourceName}",
                    body = if (subscribed) {
                        "This is a subscribed calendar, so it can't be changed here."
                    } else {
                        "Change events on this calendar in ${e.serviceName} on your phone."
                    },
                )
            }
            e.readOnlyReason == ReadOnlyReason.Recurring -> Note(
                icon = "event_repeat",
                title = "Repeating event",
                body = "Edit repeating events in ${e.serviceName} on your phone.",
            )
            e.untagged -> Note(
                icon = "smartphone",
                title = "Added from a phone",
                body = "Showing as Family until someone assigns it.",
                background = c.accentSoft,
                iconTint = c.accent,
            ) {
                Spacer(Modifier.height(CalendarDimens.assignTop))
                if (mode == DetailMode.ChoosingPerson) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.personChipGap),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        people.forEach { person -> HhPersonChip(person.name, Color(person.color), "assign_${person.name}", enabled = !busy) { onAssign(person) } }
                    }
                } else {
                    AssignButton(enabled = !busy, onClick = onChoosePerson)
                }
            }
        }
        if (e.editable) {
            Spacer(Modifier.weight(1f))
            if (mode == DetailMode.ConfirmingDelete) {
                DeleteConfirmation(e, busy, onKeep, onConfirmDelete)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    DeleteButton(enabled = !busy, tag = "detail_delete", onClick = onDelete)
                    PrimaryButton("Edit", "edit", enabled = !busy, tag = "detail_edit", onClick = onEdit, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Header(e: EventUi, onClose: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.sheetHeaderGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
            ColourBar(Color(e.person.color), width = CalendarDimens.sheetTitleBar)
            Spacer(Modifier.width(CalendarDimens.sheetTitleBarGap))
            Column {
                if (e.syncing) {
                    SyncingPill(e.connectionLabel)
                    Spacer(Modifier.height(CalendarDimens.syncingPillBottom))
                }
                Text(e.title, style = CalendarType.sheetTitle, color = c.ink)
            }
        }
        HhCloseButton(onClick = onClose)
    }
}

@Composable
private fun SyncingPill(connectionLabel: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.syncingPillIconGap),
        modifier = Modifier
            .testTag("detail_syncing")
            .height(CalendarDimens.syncingPillHeight)
            .clip(RoundedCornerShape(CalendarDimens.syncingPillRadius))
            .background(c.surf2)
            .padding(horizontal = CalendarDimens.syncingPillPaddingH),
    ) {
        HhIcon("cloud_upload", size = CalendarDimens.syncingPillIcon, tint = c.mute)
        Text("Syncing to $connectionLabel…", style = CalendarType.syncingPill, color = c.mute, maxLines = 1)
    }
}

@Composable
private fun InfoCard(detail: EventDetailUi) {
    val e = detail.event
    HhCard(
        modifier = Modifier.fillMaxWidth().testTag("detail_info"),
        radius = CalendarDimens.infoRadius,
        padding = PaddingValues(horizontal = CalendarDimens.infoPaddingH, vertical = CalendarDimens.infoPaddingV),
    ) {
        InfoRow("schedule", "When") { Value(detail.whenLabel) }
        Divider()
        InfoRow("person", "For") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.infoDotGap)) {
                Box(Modifier.size(CalendarDimens.infoDot).clip(CircleShape).background(Color(e.person.color)))
                Value(e.person.name)
            }
        }
        Divider()
        InfoRow("edit_note", "Created by") { Value(e.createdBy) }
        Divider()
        InfoRow("calendar_month", "Calendar") { Value(calendarLabel(e)) }
        if (e.recurring) {
            Divider()
            InfoRow("repeat", "Repeats") { Value(e.repeats) }
        }
    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: @Composable () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = CalendarDimens.infoRowMin),
    ) {
        HhIcon(icon, size = CalendarDimens.infoIcon, tint = c.mute)
        Spacer(Modifier.width(CalendarDimens.infoIconGap))
        Text(label, style = CalendarType.infoLabel, color = c.mute, maxLines = 1, modifier = Modifier.width(CalendarDimens.infoLabelWidth))
        value()
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = CalendarType.infoValue, color = Culvery.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(CalendarDimens.infoDivider).background(Culvery.colors.line))
}

@Composable
private fun Note(
    icon: String,
    title: String,
    body: String,
    background: Color = Culvery.colors.surf,
    iconTint: Color = Culvery.colors.mute,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Culvery.colors
    HhCard(
        modifier = Modifier.fillMaxWidth().testTag("detail_note"),
        radius = CalendarDimens.noteRadius,
        color = background,
        padding = PaddingValues(horizontal = CalendarDimens.notePaddingH, vertical = CalendarDimens.notePaddingV),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.noteIconGap)) {
            HhIcon(icon, size = CalendarDimens.noteIcon, tint = iconTint)
            Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.noteTextGap)) {
                Text(title, style = CalendarType.noteTitle, color = c.ink)
                Text(body, style = CalendarType.noteBody, color = c.mute)
            }
        }
        content()
    }
}

@Composable
private fun AssignButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("detail_assign")
            .height(CalendarDimens.assignButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.assignButtonRadius))
            .background(c.accent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CalendarDimens.assignButtonPaddingH),
    ) {
        Text("Assign to…", style = CalendarType.assignButton, color = c.accentInk, maxLines = 1)
    }
}

@Composable
private fun DeleteConfirmation(e: EventUi, busy: Boolean, onKeep: () -> Unit, onConfirmDelete: () -> Unit) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.confirmGap),
        modifier = Modifier
            .testTag("detail_confirm")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.confirmRadius))
            .background(c.dangerSoft)
            .padding(CalendarDimens.confirmPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.noteIconGap)) {
            HhIcon("delete", size = CalendarDimens.footerIcon, tint = c.danger)
            Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.noteTextGap)) {
                Text("Delete this event?", style = CalendarType.confirmTitle, color = c.ink)
                Text(
                    "“${e.title}” will be removed from ${e.serviceName} for everyone.",
                    style = CalendarType.noteBody,
                    color = c.mute,
                )
            }
        }
        // Keep event sits on the left, where Delete was, so a double tap on Delete is harmless.
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhSheetButton("Keep event", ButtonTone.Quiet, enabled = !busy, tag = "detail_keep", onClick = onKeep, modifier = Modifier.weight(1f))
            HhSheetButton(
                "Delete event", ButtonTone.Destroy, enabled = !busy, tag = "detail_confirm_delete", onClick = onConfirmDelete,
                modifier = Modifier.weight(1f), icon = "delete_forever",
            )
        }
    }
}
