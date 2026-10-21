package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.calendar.ALL_DAY_LABEL
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.checkSentence
import uk.co.siland.culvery.capability.calendar.firstDates
import uk.co.siland.culvery.capability.calendar.needsLine
import uk.co.siland.culvery.capability.calendar.timeValue
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.clockText

internal const val FIRST_FEW = "First few"
internal const val ON_THE_WEEK = "On the week"

/** "+ 8 more": the dates of a counted series after the first few. */
internal fun moreDates(n: Int): String = "+ $n more"

// Everyone is Family; with nobody chosen yet the preview has nobody to tint it, so it shows as Family too.
private fun chosenPeople(form: EditorForm, byId: Map<PersonId, Person>): List<Person> =
    form.who.mapNotNull { byId[it] }.ifEmpty { listOf(Person.Family) }

/** The event as the week would show it (handoff 6g "On the week"): never opened, so its ref names nothing. */
internal fun previewUi(form: EditorForm, byId: Map<PersonId, Person>): EventUi {
    val start = form.startTime
    return EventUi(
        ref = EventRef("", "", "preview"),
        title = form.title.trim(),
        timeLabel = timeValue(form),
        startLabel = start?.let(::clockText) ?: ALL_DAY_LABEL,
        people = chosenPeople(form, byId),
        allDay = start == null,
        recurring = form.repeat != RepeatChoice.Never,
        startSort = 0,
    )
}

/**
 * Handoff 6g: the event said back in a sentence, its first few dates with who it's for, "+ 8 more" for a counted
 * series, and the week's chip for it; until it can be added, only what it still needs. Nothing here is a button: Add
 * event at the foot is the one action.
 */
@Composable
internal fun CheckPane(form: EditorForm, household: EditorPeople) {
    val c = Culvery.colors
    val who = chosenPeople(form, household.byId)
    needsLine(form.missing)?.let {
        Text(it, style = CalendarType.whoHint, color = c.mute, modifier = Modifier.testTag("check_needs"))
        return
    }
    val (dates, more) = firstDates(form)
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        Text(checkSentence(form, household.people), style = CalendarType.checkSentence, color = c.ink, modifier = Modifier.testTag("check_sentence"))
        if (dates.isNotEmpty()) {
            // A one-off's single date needs no heading over it.
            if (form.repeat != RepeatChoice.Never) CapsHeading(FIRST_FEW)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(EditorDimens.checkDateGap),
                verticalArrangement = Arrangement.spacedBy(EditorDimens.checkDateGap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                dates.forEachIndexed { i, date -> DateChip(date.format(SHORT_DAY), who, "check_date_$i") }
                if (more > 0) Text(moreDates(more), style = CalendarType.checkDate, color = c.mute, modifier = Modifier.testTag("check_more"))
            }
        }
        CapsHeading(ON_THE_WEEK)
        Box(Modifier.testTag("check_chip").width(EditorDimens.checkChipWidth)) {
            EventChip(previewUi(form, household.byId), form.day ?: form.today, onOpen = null)
        }
    }
}

/** A 40 dp `surf2` pill, not tappable: who it's for as dots, then the date. */
@Composable
private fun DateChip(date: String, who: List<Person>, tag: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.checkDotGap),
        modifier = Modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) {}
            .height(EditorDimens.checkDateHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(c.surf2)
            .padding(horizontal = EditorDimens.checkDatePaddingH),
    ) {
        PersonDots(who, EditorDimens.checkDot, ring = c.surf2, dotTag = "check_date_dot")
        Text(date, style = CalendarType.checkDate, color = c.ink, maxLines = 1)
    }
}
