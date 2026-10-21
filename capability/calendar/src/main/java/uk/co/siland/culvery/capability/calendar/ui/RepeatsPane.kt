package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.calendar.CHOOSE_A_DAY
import uk.co.siland.culvery.capability.calendar.EDITOR_REPEATS
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.MAX_TIMES
import uk.co.siland.culvery.capability.calendar.MIN_TIMES
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.RepeatEnd
import uk.co.siland.culvery.capability.calendar.wording
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhSegmented
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

internal const val KEEPS_GOING = "Keeps going"
internal const val STOPS_AFTER = "Stops after"
internal const val CHOOSE_A_DAY_FIRST = "Choose a day first."

/** "To skip one date, change it in Google Calendar on your phone." */
internal fun skipOneDateLine(service: String): String = "To skip one date, change it in $service on your phone."

/**
 * Handoff 6f: the repeats worded from the chosen day as radio rows; while it repeats, Keeps going | Stops after, and
 * for Stops after one stepper with the count and the last date; then where to skip one date ([service]). Doesn't
 * repeat calls [onPicked]; a repeat doesn't, so Ends can still be set. With no day it asks for one, and its button
 * calls [onChooseDay].
 */
@Composable
internal fun RepeatsPane(form: EditorForm, service: String, onPicked: () -> Unit, onChooseDay: () -> Unit) {
    val day = form.day
    if (day == null) {
        NeedsADay(onChooseDay)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.repeatRowGap), modifier = Modifier.selectableGroup()) {
            EDITOR_REPEATS.forEach { choice ->
                RepeatRow(choice.wording(day), selected = form.repeat == choice, tag = "repeat_${choice.name}") {
                    form.chooseRepeat(choice)
                    if (choice == RepeatChoice.Never) onPicked()
                }
            }
        }
        if (form.repeat != RepeatChoice.Never) {
            val after = form.repeatEnd as? RepeatEnd.After
            HhSegmented(
                options = listOf(KEEPS_GOING, STOPS_AFTER),
                selected = if (after != null) 1 else 0,
                onSelect = { if (it == 0) form.keepsGoing() else form.stopsAfter() },
                tag = "ends_mode",
            )
            if (after != null) Stepper(form, after.times)
        }
        Text(skipOneDateLine(service), style = CalendarType.whoHint, color = Culvery.colors.mute, modifier = Modifier.testTag("repeats_hint"))
    }
}

@Composable
private fun NeedsADay(onChooseDay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        Text(CHOOSE_A_DAY_FIRST, style = CalendarType.whoHint, color = Culvery.colors.mute, modifier = Modifier.testTag("repeats_need_day"))
        HhButton(CHOOSE_A_DAY, onChooseDay, Modifier.testTag("repeats_choose_day"), icon = Icons.CALENDAR_MONTH, on = ButtonSurface.Surf)
    }
}

/** 56 dp, `surf2`, a radio then the words; chosen, `accentSoft` with a 2 dp `accent` ring inside. */
@Composable
private fun RepeatRow(words: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(EditorDimens.repeatRowRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.radioGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected }
            .hhClickable(onClick, role = Role.RadioButton)
            .fillMaxWidth()
            .heightIn(min = EditorDimens.repeatRowHeight)
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surf2)
            .then(if (selected) Modifier.border(EditorDimens.ring, c.accent, shape) else Modifier)
            .padding(horizontal = EditorDimens.repeatRowPaddingH),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(EditorDimens.radio).border(EditorDimens.ring, if (selected) c.accent else c.mute, CircleShape),
        ) {
            if (selected) Box(Modifier.size(EditorDimens.radioDot).clip(CircleShape).background(c.accent))
        }
        Text(words, style = CalendarType.repeatOption, color = c.ink, modifier = Modifier.weight(1f))
    }
}

/** − "12 times · last on Sat 12 Dec" +: held, a button keeps stepping; each end disables its button. */
@Composable
private fun Stepper(form: EditorForm, times: Int) {
    val c = Culvery.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorDimens.stepperGap)) {
        StepButton(Icons.REMOVE, FEWER_TIMES, "ends_fewer", enabled = times > MIN_TIMES, onStep = form::fewerTimes)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .testTag("ends_count")
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .heightIn(min = ControlTokens.chipHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(c.surf2)
                .padding(horizontal = EditorDimens.stepperPaddingH),
        ) {
            Text(form.stepperLabel.orEmpty(), style = CalendarType.repeatCount, color = c.ink, maxLines = 1)
        }
        StepButton(Icons.ADD, MORE_TIMES, "ends_more", enabled = times < MAX_TIMES, onStep = form::moreTimes)
    }
}

@Composable
private fun StepButton(icon: String, spoken: String, tag: String, enabled: Boolean, onStep: () -> Unit) {
    HhRoundButton(
        icon = icon,
        spoken = spoken,
        onClick = onStep,
        modifier = Modifier.testTag(tag),
        enabled = enabled,
        on = ButtonSurface.Surf,
        size = ControlTokens.chipHeight,
        iconSize = ButtonTokens.icon,
        repeatWhileHeld = true,
    )
}
