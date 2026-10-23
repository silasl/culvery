package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CHOOSE_A_DAY
import uk.co.siland.culvery.capability.calendar.CopiedRow
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.hhClickable

/** A row's paint (handoff §1): the one the pane asks about, one that is answered, or one still on its placeholder. */
enum class RowState { Active, Done, NotYet }

internal fun rowState(active: Boolean, set: Boolean): RowState = when {
    active -> RowState.Active
    set -> RowState.Done
    else -> RowState.NotYet
}

/** A pill after a row's value: "Clash" on Starts. */
internal data class RowTag(val text: String, val icon: String?, val fill: Color, val ink: Color)

internal const val CLASH = "Clash"
internal const val TOO_LONG = "Too long"
internal const val COPIED = "Copied"

/** TalkBack's word for a locked row, whose value says where its dates change instead (9 §3.1). */
internal const val LOCKED_SPAN = "dates locked, as it runs over more than 60 days"

/** Handoff 6i's tag on Starts: `weather` words and `warning` on a `weather` tint, made opaque over `surf` so an active row's fill doesn't lower its contrast. */
@Composable
internal fun clashTag(): RowTag {
    val c = Culvery.colors
    return RowTag(CLASH, Icons.WARNING, c.weather.copy(alpha = HhAlpha.clash(c)).compositeOver(c.surf), c.weather)
}

// The Clash tag's shape in `accentSoft` and `accent`.
@Composable
private fun accentTag(text: String, icon: String): RowTag = Culvery.colors.let { RowTag(text, icon, it.accentSoft, it.accent) }

/** What "Use this" filled in (9c §4.4), while [row] is still as it was copied. */
@Composable
internal fun EditorForm.copiedTag(row: CopiedRow): RowTag? = if (row in copied) accentTag(COPIED, Icons.CONTENT_COPY) else null

/** The Repeats row's tag when a change to the When ruled out the chosen repeat (9c §3.3): the Copied tag's colours. */
@Composable
internal fun tooLongTag(): RowTag = accentTag(TOO_LONG, Icons.INFO)

/**
 * One of the event's rows (handoff §1 Layout): an [icon], the [label] over the [value] on one line, an optional [tag],
 * and a chevron; [onClick] null is a locked row, with a lock and no pane to open, which TalkBack hears as [LOCKED_SPAN].
 * With the keyboard up ([typing]) it is shorter and has no label. Its parts are tagged [testTag] with `_value`, `_icon`,
 * `_tag` and `_lock`.
 */
@Composable
internal fun EditorRow(
    testTag: String,
    icon: String,
    label: String,
    value: String,
    active: Boolean,
    set: Boolean,
    typing: Boolean,
    onClick: (() -> Unit)?,
    tag: RowTag? = null,
) {
    val c = Culvery.colors
    val state = rowState(active, set)
    val shape = ListRowTokens.shape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.rowIconGap),
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth()
            .heightIn(min = if (typing) EditorDimens.rowHeightTyping else EditorDimens.rowHeight)
            .clip(shape)
            .background(if (state == RowState.Active) c.accentSoft else c.surf)
            .then(if (state == RowState.Active) Modifier.border(EditorDimens.ring, c.accent, shape) else Modifier)
            .semantics(mergeDescendants = true) {
                selected = active
                if (onClick == null) stateDescription = LOCKED_SPAN
            }
            .then(if (onClick != null) Modifier.hhClickable(onClick) else Modifier)
            .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd),
    ) {
        HhIcon(icon, size = EditorDimens.rowIcon, tint = if (state == RowState.NotYet) c.mute else c.accent, modifier = Modifier.testTag("${testTag}_icon"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EditorDimens.rowLabelGap)) {
            if (!typing) {
                // Drawn in capitals, heard as the word.
                Text(label.uppercase(), style = CalendarType.rowLabel, color = c.mute, maxLines = 1, modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(label) })
            }
            Text(
                value,
                style = CalendarType.rowValue,
                color = if (set) c.ink else c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("${testTag}_value"),
            )
        }
        if (tag != null) RowTagPill(tag, Modifier.testTag("${testTag}_tag"))
        if (onClick != null) {
            HhIcon(Icons.CHEVRON_RIGHT, size = EditorDimens.rowEndIcon, tint = c.mute)
        } else {
            HhIcon(Icons.LOCK, size = EditorDimens.rowEndIcon, tint = c.mute, modifier = Modifier.testTag("${testTag}_lock"))
        }
    }
}

/** What TalkBack hears for the Ends row before a Starts day is chosen. */
internal const val CHOOSE_A_START_DAY_FIRST = "choose a start day first"

/**
 * Starts or Ends (9c §3.1): an [icon], then the [label] over the [date] and, when there is one, the [time], each its own
 * button ([onDate], [onTime]) heard as "Starts, Saturday 26 September" ([spokenDate]) and "Starts at 10:00". With no date and
 * no [onDate] the row waits: its label alone, not a button. [locked] reads [date] as the span's dates, with a lock and no
 * button. With the keyboard up ([typing]) it is shorter and has no label. Parts are tagged [testTag] with `_date`, `_time`,
 * `_icon`, `_tag` and `_lock`.
 */
@Composable
internal fun EditorWhenRow(
    testTag: String,
    icon: String,
    label: String,
    date: String?,
    time: String?,
    spokenDate: String?,
    active: Boolean,
    set: Boolean,
    typing: Boolean,
    onDate: (() -> Unit)?,
    onTime: (() -> Unit)?,
    tag: RowTag? = null,
    locked: Boolean = false,
) {
    if (locked) {
        EditorRow(testTag, icon, label, date.orEmpty(), active = false, set = true, typing = typing, onClick = null, tag = tag)
        return
    }
    val c = Culvery.colors
    val state = rowState(active, set)
    val shape = ListRowTokens.shape
    val waiting = date == null && onDate == null
    val height = if (typing) EditorDimens.rowHeightTyping else EditorDimens.rowHeight
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.rowIconGap),
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(shape)
            .background(if (state == RowState.Active) c.accentSoft else c.surf)
            .then(if (state == RowState.Active) Modifier.border(EditorDimens.ring, c.accent, shape) else Modifier)
            .semantics { selected = active }
            .then(
                if (waiting) Modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $CHOOSE_A_START_DAY_FIRST" } else Modifier,
            )
            .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd),
    ) {
        HhIcon(icon, size = EditorDimens.rowIcon, tint = if (state == RowState.NotYet) c.mute else c.accent, modifier = Modifier.testTag("${testTag}_icon"))
        if (waiting) {
            Text(label, style = CalendarType.rowValue, color = c.mute, maxLines = 1, modifier = Modifier.weight(1f))
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(EditorDimens.rowLabelGap, Alignment.CenterVertically),
                modifier = Modifier
                    .testTag("${testTag}_date")
                    .weight(1f)
                    .heightIn(min = height)
                    .then(if (onDate != null) Modifier.hhClickable(onDate, spoken = "$label, ${spokenDate ?: CHOOSE_A_DAY}") else Modifier),
            ) {
                if (!typing) {
                    Text(label.uppercase(), style = CalendarType.rowLabel, color = c.mute, maxLines = 1, modifier = Modifier.semantics { hideFromAccessibility() })
                }
                Text(
                    date ?: CHOOSE_A_DAY,
                    style = CalendarType.rowValue,
                    color = if (date != null) c.ink else c.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                )
            }
            if (time != null && onTime != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag("${testTag}_time")
                        .heightIn(min = height)
                        .hhClickable(onTime, spoken = "$label at $time")
                        .padding(horizontal = EditorDimens.rowTimePaddingH),
                ) {
                    Text(time, style = CalendarType.rowValue, color = c.ink, maxLines = 1, modifier = Modifier.semantics { hideFromAccessibility() })
                }
            }
        }
        if (tag != null) RowTagPill(tag, Modifier.testTag("${testTag}_tag"))
    }
}

/** Starts carries one tag (9c §3.1): a clash hides Copied until it clears. */
internal fun startsTag(clash: RowTag?, copied: RowTag?): RowTag? = clash ?: copied

@Composable
private fun RowTagPill(tag: RowTag, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.tagIconGap),
        modifier = modifier
            .height(EditorDimens.tagHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(tag.fill)
            .padding(horizontal = EditorDimens.tagPaddingH),
    ) {
        if (tag.icon != null) HhIcon(tag.icon, size = EditorDimens.tagIcon, tint = tag.ink)
        Text(tag.text, style = CalendarType.rowTag, color = tag.ink, maxLines = 1)
    }
}
