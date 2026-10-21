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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

internal const val SKIP = "Skip"

/** The pane's question for [step] (handoff §1 The frames). */
internal fun questionFor(step: EditorStep, form: EditorForm): String = when (step) {
    EditorStep.Who -> "Who’s it for?"
    EditorStep.Day -> if (form.multiDay) "Which days?" else "Which day?"
    EditorStep.Time -> "What time?"
    EditorStep.Repeats -> "Does it repeat?"
    EditorStep.Check -> "Check it"
}

/** The pane for [step]: its question over what answers it. */
@Composable
internal fun EditorPaneFor(
    step: EditorStep,
    form: EditorForm,
    flow: EditorFlow,
    members: List<Member>,
    held: Held?,
    childOnly: PersonId?,
    childName: String?,
    busy: Boolean,
    onRefusedWho: () -> Unit,
    onPicker: (EditorPicker) -> Unit,
) {
    EditorPane(questionFor(step, form), flow, skip = step == EditorStep.Time || step == EditorStep.Repeats) {
        when (step) {
            EditorStep.Who -> WhoPane(form, members, childOnly, childName, busy, onRefusedWho)
            EditorStep.Day -> DayPane(form, held, members.map { it.person }, onPicked = flow::moveOnSoon)
            EditorStep.Time -> TimePane(form, held, members.map { it.person }, onPicker, onPicked = flow::moveOnSoon)
            else -> Unit
        }
    }
}

/**
 * The right-hand pane (handoff §1): `surf`, scrolling inside itself, with the [question] and, on its right, Skip (when
 * [skip]: it moves on and changes nothing) and a quiet Next. The question is the pane's title, so TalkBack speaks a new
 * one as the pane changes (9 §3.1).
 */
@Composable
internal fun EditorPane(question: String, flow: EditorFlow, skip: Boolean, body: @Composable ColumnScope.() -> Unit) {
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
                if (skip) SkipButton(flow::next)
                if (flow.nextStep != null) {
                    HhButton(
                        flow.nextLabel(),
                        flow::next,
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
