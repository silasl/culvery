package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ReadOnlyReason
import uk.co.siland.culvery.capability.calendar.whoLine
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.KEEP
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlType
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPersonChip
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.hhClickable

enum class DetailMode { Idle, ChoosingPerson, ConfirmingDelete }

internal const val CANT_CHANGE_HERE = "Can’t change here"
internal const val CHOOSE_WHO = "Choose who it’s for"
internal const val CHANGE_WHO = "Change who"

/** The Calendar row's value: the source's name, said to be out of reach when the tablet can't change it (4d-2 Decision 2g). */
internal fun calendarLabel(event: EventUi): String = when (event.readOnlyReason) {
    ReadOnlyReason.OtherCalendar, ReadOnlyReason.NotMaster -> "${event.sourceName} (can’t change here)"
    else -> event.sourceName
}

/**
 * Hand-off §7 "Sheet 1 — Event detail". Stateless: the host decides [mode] and [busy]. Buttons are never hidden
 * for permission reasons; the checks happen when they are tapped. Delete runs the guard ([onDelete]); only the
 * confirmation's "Delete event" deletes ([onConfirmDelete]). Change ([onEdit]) shows exactly where Delete does and
 * opens the editor without a PIN (2b-2 design D12); on an event it can change, the For row opens it on Who ([onChangeWho], 9 §3.3).
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
    onChangeWho: () -> Unit,
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
        modifier = modifier.testTag("detail_sheet").semantics { paneTitle = EVENT_DETAILS },
    ) {
        // The title has no line limit, so the header scrolls with the body; only the footer stays put (4d-1 §6.4).
        Column(
            verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Header(e, onClose)
            InfoCard(detail, onChangeWho.takeIf { e.editable && !busy })
            when {
                e.readOnlyReason == ReadOnlyReason.OtherCalendar || e.readOnlyReason == ReadOnlyReason.NotMaster -> Note(
                    icon = Icons.LOCK,
                    title = CANT_CHANGE_HERE,
                    body = if (e.readOnlyReason == ReadOnlyReason.OtherCalendar) {
                        "This calendar can only be changed by whoever runs it."
                    } else {
                        "Change events on this calendar in ${e.serviceName} on your phone."
                    },
                )
                e.readOnlyReason == ReadOnlyReason.Recurring -> Note(
                    icon = Icons.EVENT_REPEAT,
                    title = "Repeating event",
                    body = "Change repeating events in ${e.serviceName} on your phone." +
                        if (e.assignable) " Choosing who it’s for changes every time it repeats." else "",
                ) {
                    if (e.assignable) ChooseWho(people, chosenFor(e), mode, busy, onChoosePerson, onAssign)
                }
                // A phone event keeps its chips once the first tap tags it, so a set can be built (9 §3.4).
                e.untagged || (mode == DetailMode.ChoosingPerson && e.editable) -> Note(
                    icon = Icons.MOBILE,
                    title = "Added from a phone",
                    body = "Showing as Family until someone chooses who it’s for.".takeIf { e.untagged },
                    background = c.accentSoft,
                    iconTint = c.accent,
                ) {
                    ChooseWho(people, chosenFor(e), mode, busy, onChoosePerson, onAssign)
                }
            }
        }
        if (e.editable) {
            if (mode == DetailMode.ConfirmingDelete) {
                HhConfirmCard(
                    tag = "detail_confirm",
                    title = DELETE_QUESTION,
                    keep = KEEP, keepTag = "detail_keep",
                    confirm = "Delete event", confirmTag = "detail_confirm_delete", confirmIcon = Icons.DELETE_FOREVER,
                    busy = busy, onKeep = onKeep, onConfirm = onConfirmDelete,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.noteIconGap)) {
                        HhIcon(Icons.DELETE, size = CalendarDimens.footerIcon, tint = c.danger)
                        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.noteTextGap)) {
                            Text(DELETE_QUESTION, style = ControlType.confirmTitle, color = c.ink)
                            Text(
                                "“${e.title}” will be removed from ${e.serviceName} for everyone.",
                                style = CalendarType.noteBody,
                                color = c.mute,
                                // A title synced from a phone can be any length; past three lines it would push Keep off the sheet.
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    DeleteButton(enabled = !busy, tag = "detail_delete", onClick = onDelete)
                    HhButton(
                        "Change", onEdit, Modifier.weight(1f).testTag("detail_edit"),
                        tone = ButtonTone.Primary, size = ButtonSize.Large, icon = Icons.EDIT, enabled = !busy, on = ButtonSurface.Bg,
                    )
                }
            }
        }
    }
}

/** Who the chips show as chosen: the event's people, or nobody while it is untagged and only shows as its fallback. */
internal fun chosenFor(event: EventUi): List<PersonId> = if (event.untagged) emptyList() else event.people.map { it.id }

/** Choose who it’s for, then a toggle for each of the household's [people] (never Family) and Family last, once it is tapped (9 §3.4). */
@Composable
private fun ChooseWho(
    people: List<Person>,
    chosen: List<PersonId>,
    mode: DetailMode,
    busy: Boolean,
    onChoosePerson: () -> Unit,
    onAssign: (Person) -> Unit,
) {
    Spacer(Modifier.height(CalendarDimens.assignTop))
    if (mode == DetailMode.ChoosingPerson) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.personChipGap),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            (people + Person.Family).forEach { person ->
                HhPersonChip(person.name, Color(person.color), "assign_${person.name}", enabled = !busy, selected = person.id in chosen) { onAssign(person) }
            }
        }
    } else {
        HhButton(
            CHOOSE_WHO, onChoosePerson, Modifier.testTag("detail_assign"),
            tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf,
        )
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
            StripedBar(e.people.map { Color(it.color) }, width = CalendarDimens.sheetTitleBar)
            Spacer(Modifier.width(CalendarDimens.sheetTitleBarGap))
            Column {
                if (e.syncing) {
                    SyncingPill(e.serviceName)
                    Spacer(Modifier.height(CalendarDimens.syncingPillBottom))
                }
                Text(e.title, style = CalendarType.sheetTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            }
        }
        HhCloseButton(onClick = onClose)
    }
}

@Composable
private fun SyncingPill(service: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.syncingPillIconGap),
        modifier = Modifier
            .testTag("detail_syncing")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .height(CalendarDimens.syncingPillHeight)
            .clip(RoundedCornerShape(CalendarDimens.syncingPillRadius))
            .background(c.surf2)
            .padding(horizontal = CalendarDimens.syncingPillPaddingH),
    ) {
        HhIcon(Icons.CLOUD_UPLOAD, size = CalendarDimens.syncingPillIcon, tint = c.mute)
        Text("Saving to $service…", style = CalendarType.syncingPill, color = c.mute, maxLines = 1)
    }
}

@Composable
private fun InfoCard(detail: EventDetailUi, onChangeWho: (() -> Unit)?) {
    val e = detail.event
    HhCard(
        modifier = Modifier.fillMaxWidth().testTag("detail_info"),
        radius = CalendarDimens.infoRadius,
        padding = PaddingValues(horizontal = CalendarDimens.infoPaddingH, vertical = CalendarDimens.infoPaddingV),
    ) {
        InfoRow(Icons.SCHEDULE, "When") { Value(detail.whenLabel) }
        Divider()
        InfoRow(Icons.PERSON, "For", Modifier.testTag("detail_for"), onClick = onChangeWho, clickLabel = CHANGE_WHO) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.infoDotGap)) {
                PersonDots(e.people, CalendarDimens.infoDot, ring = Culvery.colors.surf, dotTag = "for_dot")
                Value(whoLine(e.people))
            }
        }
        Divider()
        InfoRow(Icons.EDIT_NOTE, "Created by") { Value(e.createdBy) }
        Divider()
        InfoRow(Icons.CALENDAR_MONTH, "Calendar") { Value(calendarLabel(e)) }
        if (e.recurring) {
            Divider()
            InfoRow(Icons.REPEAT, "Repeats") { Value(e.repeats) }
        }
    }
}

@Composable
private fun InfoRow(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    value: @Composable () -> Unit,
) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CalendarDimens.infoRowMin)
            .semantics(mergeDescendants = true) {}
            .then(if (onClick != null) Modifier.hhClickable(onClick, label = clickLabel) else Modifier),
    ) {
        HhIcon(icon, size = CalendarDimens.infoIcon, tint = c.mute)
        Spacer(Modifier.width(CalendarDimens.infoIconGap))
        Text(label, style = CalendarType.infoLabel, color = c.mute, maxLines = 2, modifier = Modifier.widthIn(min = CalendarDimens.infoLabelWidth))
        Spacer(Modifier.width(CalendarDimens.infoLabelGap))
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
    body: String?,
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
                if (body != null) Text(body, style = CalendarType.noteBody, color = c.mute)
            }
        }
        content()
    }
}
