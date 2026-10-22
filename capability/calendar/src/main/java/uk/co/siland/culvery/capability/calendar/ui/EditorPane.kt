package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.bannerLine
import uk.co.siland.culvery.capability.calendar.quickPicksFor
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

internal const val SKIP = "Skip"

/** The pane's question for [step] (handoff §1 The frames, 9c §3.2). */
internal fun questionFor(step: EditorStep): String = when (step) {
    EditorStep.Who -> "Who’s it for?"
    EditorStep.StartDate -> STARTS_DATE_QUESTION
    EditorStep.StartTime -> STARTS_TIME_QUESTION
    EditorStep.EndDate -> ENDS_DATE_QUESTION
    EditorStep.EndTime -> ENDS_TIME_QUESTION
    EditorStep.Repeats -> "Does it repeat?"
    EditorStep.Check -> "Check it"
}

/** The household as the panes read it: [people] in order, their ids in [order], and [byId] with Family too. */
internal class EditorPeople(members: List<Member>) {
    val people: List<Person> = members.map { it.person }
    val order: List<PersonId> = people.map { it.id }
    val byId: Map<PersonId, Person> = (people + Person.Family).associateBy { it.id }
}

/** The pane for [step]: its question over what answers it. A pick made while [busy] saving doesn't move on. */
@Composable
internal fun EditorPaneFor(
    step: EditorStep,
    form: EditorForm,
    flow: EditorFlow,
    members: List<Member>,
    household: EditorPeople,
    held: Held?,
    service: String,
    childOnly: PersonId?,
    childName: String?,
    busy: Boolean,
    onRefusedWho: () -> Unit,
) {
    val except = (form.mode as? EditorForm.Mode.Edit)?.original?.ref
    val context = remember(held, household, form.who, except, form.zone, form.today) {
        WhenContext(held, household, form.who, except, form.zone, form.today)
    }
    val skip: (() -> Unit)? = when (step) {
        EditorStep.EndDate, EditorStep.EndTime -> flow::skipEnds
        EditorStep.Repeats -> flow::next
        else -> null
    }
    EditorPane(questionFor(step), flow, skip) {
        when (step) {
            EditorStep.Who -> WhoPane(form, members, childOnly, childName, busy, onRefusedWho)
            EditorStep.StartDate -> StartDatePane(form.eventWhen, context, copiedBanner(form), copiedQuickPicks(form)) {
                form.pickStartDate(it)
                if (!busy) flow.moveOnAfterDate(EditorStep.StartTime)
            }
            EditorStep.StartTime -> StartTimePane(form.eventWhen, context, form::stepStart, form::useFreeStart)
            EditorStep.EndDate -> EndDatePane(form.eventWhen, context) {
                form.pickEndDate(it)
                if (!busy) flow.moveOnAfterDate(EditorStep.EndTime)
            }
            EditorStep.EndTime -> EndTimePane(form.eventWhen, context, form::stepEnd, form::useFreeStart)
            EditorStep.Repeats -> RepeatsPane(form, service, onPicked = { flow.moveOnSoon(EditorStep.Check) }, onChooseDay = { flow.open(EditorStep.StartDate) })
            EditorStep.Check -> CheckPane(form, household)
        }
    }
}

/**
 * The right-hand pane (handoff §1): `surf`, scrolling inside itself, with the [question] and, on its right, Skip (when
 * there is a [skip]: it moves on and changes nothing) and a quiet Next. The question is the pane's title, so TalkBack speaks a new
 * one as the pane changes (9 §3.1).
 */
@Composable
internal fun EditorPane(
    question: String,
    flow: EditorFlow,
    skip: (() -> Unit)?,
    next: PaneNext? = flow.nextStep?.let { PaneNext(flow.nextLabel(), flow::next) },
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap),
        modifier = Modifier
            .testTag("editor_pane")
            .fillMaxSize()
            .clip(RoundedCornerShape(EditorDimens.paneRadius))
            .background(c.surf)
            .verticalScroll(rememberScrollState())
            .padding(
                start = EditorDimens.panePaddingH,
                end = EditorDimens.panePaddingH,
                top = EditorDimens.panePaddingTop,
                bottom = EditorDimens.panePaddingBottom,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorDimens.paneHeaderGap),
            modifier = Modifier.fillMaxWidth().heightIn(min = EditorDimens.paneHeader),
        ) {
            Text(
                question,
                style = CalendarType.paneQuestion,
                color = c.ink,
                modifier = Modifier.weight(1f).testTag("editor_question").semantics {
                    paneTitle = question
                    heading()
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorDimens.paneButtonGap)) {
                if (skip != null) SkipButton(skip)
                if (next != null) {
                    HhButton(
                        next.label,
                        next.onClick,
                        Modifier.testTag("editor_next"),
                        trailingIcon = Icons.ARROW_FORWARD,
                        on = ButtonSurface.Surf,
                    )
                }
            }
        }
        body()
    }
}

/** What the pane's quiet Next says and does. */
internal class PaneNext(val label: String, val onClick: () -> Unit)

/** A heading in capitals, heard as the word. */
@Composable
internal fun CapsHeading(name: String, modifier: Modifier = Modifier) {
    Text(
        name.uppercase(),
        style = CalendarType.rowLabel,
        color = Culvery.colors.mute,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(name)
            heading()
        },
    )
}

/** A `lock` and a muted line, read as one; [live] says it as it appears. */
@Composable
internal fun LockedNote(text: String, tag: String, live: Boolean = false) {
    val mute = Culvery.colors.mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.footIconGap),
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) { if (live) liveRegion = LiveRegionMode.Polite },
    ) {
        HhIcon(Icons.LOCK, size = EditorDimens.footIcon, tint = mute)
        Text(text, style = CalendarType.whoHint, color = mute)
    }
}

/** A text button in `mute`: quieter than Next. */
@Composable
private fun SkipButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("editor_skip")
            .heightIn(min = EditorDimens.paneHeader)
            .clip(RoundedCornerShape(percent = 50))
            .hhClickable(onClick)
            .padding(horizontal = EditorDimens.skipPaddingH),
    ) {
        Text(SKIP, style = CalendarType.paneSkip, color = Culvery.colors.mute, maxLines = 1)
    }
}

private fun copiedBanner(form: EditorForm): String? = form.usedSuggestion?.takeIf { form.copied.isNotEmpty() }?.let { bannerLine(it, form.copied, form.today) }

// Only a repeat that was filled in gives dates to pick.
private fun copiedQuickPicks(form: EditorForm): List<LocalDate> = form.copiedRepeat?.let { quickPicksFor(it, form.today, form.now) }.orEmpty()
