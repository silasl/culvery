package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

/** A row's paint (handoff §1): the one the pane asks about, one that is answered, or one still on its placeholder. */
enum class RowState { Active, Done, NotYet }

internal fun rowState(active: Boolean, set: Boolean): RowState = when {
    active -> RowState.Active
    set -> RowState.Done
    else -> RowState.NotYet
}

/** A pill after a row's value: "Clash" on Time. */
internal data class RowTag(val text: String, val icon: String?, val fill: Color, val ink: Color)

internal const val CLASH = "Clash"

/** TalkBack's word for a locked row, whose value says where its dates change instead (9 §3.1). */
internal const val LOCKED_SPAN = "locked, as it runs over several days"

/** Handoff 6i's tag on Time: `weather` words and `warning` on a `weather` tint, made opaque over `surf` so an active row's fill doesn't lower its contrast. */
@Composable
internal fun clashTag(): RowTag {
    val c = Culvery.colors
    return RowTag(CLASH, Icons.WARNING, c.weather.copy(alpha = HhAlpha.clash(c)).compositeOver(c.surf), c.weather)
}

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
    val shape = RoundedCornerShape(EditorDimens.rowRadius)
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
            .padding(start = EditorDimens.rowPaddingStart, end = EditorDimens.rowPaddingEnd),
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
