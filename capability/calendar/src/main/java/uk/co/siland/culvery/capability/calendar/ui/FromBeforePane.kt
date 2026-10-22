package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.Suggestion
import uk.co.siland.culvery.capability.calendar.fillLine
import uk.co.siland.culvery.capability.calendar.patternLine
import uk.co.siland.culvery.capability.calendar.spokenSuggestion
import uk.co.siland.culvery.capability.calendar.whoNow
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

internal const val FROM_BEFORE = "From before"
internal const val USE_THIS = "Use this"
internal const val FROM_BEFORE_NOTE = "A suggestion never copies the date. Or keep typing; Next on the keyboard moves to Who."

private const val FILL_LINES = 2

/**
 * The pane's body while a new event's title is focused (9c §4.3, handoff 6a): up to three remembered titles, each one
 * button that [onUse]s it, and a line saying the date is never copied. With the keyboard up ([compact]) a row drops its
 * muted line so three fit above the keys; what TalkBack hears keeps everything. [childOnly] leaves who and the repeat out of the fill line.
 */
@Composable
internal fun FromBeforePane(
    suggestions: List<Suggestion>,
    people: List<Person>,
    today: LocalDate,
    childOnly: Boolean,
    compact: Boolean,
    onUse: (Suggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.suggestionGap), modifier = Modifier.testTag("from_before_pane")) {
        suggestions.forEachIndexed { i, s -> SuggestionRow(i, s, people, today, childOnly, compact) { onUse(s) } }
        Text(
            FROM_BEFORE_NOTE,
            style = CalendarType.suggestionLine,
            color = Culvery.colors.mute,
            modifier = Modifier.testTag("from_before_note").padding(top = EditorDimens.suggestionLineGap),
        )
    }
}

/** Colour bar, title, who · pattern · when, the accent fill line, and the "Use this" pill drawn inside; heard as one button. */
@Composable
private fun SuggestionRow(index: Int, s: Suggestion, people: List<Person>, today: LocalDate, childOnly: Boolean, compact: Boolean, onUse: () -> Unit) {
    val c = Culvery.colors
    val tag = "suggestion_$index"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.suggestionRowGap),
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(EditorDimens.suggestionRadius))
            .background(c.surf2)
            .hhClickable(onUse, label = USE_THIS, spoken = spokenSuggestion(s, people, childOnly))
            .padding(EditorDimens.suggestionPadding)
            .height(IntrinsicSize.Min)
            .clearAndSetSemantics {},
    ) {
        StripedBar(s.whoNow(people), EditorDimens.suggestionBar)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EditorDimens.suggestionLineGap)) {
            Text(s.title, style = CalendarType.suggestionTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("${tag}_title"))
            if (!compact) {
                Text(
                    patternLine(s, people, today),
                    style = CalendarType.suggestionLine,
                    color = c.mute,
                    maxLines = FILL_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("${tag}_pattern"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.suggestionFillGap)) {
                HhIcon(Icons.CONTENT_COPY, size = EditorDimens.suggestionFillIcon, tint = c.accent, modifier = Modifier.padding(top = EditorDimens.suggestionLineGap))
                Text(
                    fillLine(s, people, childOnly),
                    style = CalendarType.suggestionFill,
                    color = c.accent,
                    maxLines = FILL_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("${tag}_fill"),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = EditorDimens.suggestionPillHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(c.accentSoft)
                .padding(horizontal = EditorDimens.suggestionPillPaddingH),
        ) {
            Text(USE_THIS, style = CalendarType.suggestionPill, color = c.accent, maxLines = 1, modifier = Modifier.testTag("${tag}_use"))
        }
    }
}
