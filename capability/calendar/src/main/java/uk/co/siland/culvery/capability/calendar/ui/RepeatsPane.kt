package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.capability.calendar.CHOOSE_A_DAY
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhRepeatPicker
import uk.co.siland.culvery.core.ui.Icons

internal const val CHOOSE_A_DAY_FIRST = "Choose a day first."
internal const val TOO_LONG_TO_REPEAT = "Too long to repeat that often."

/** "To skip one date, change it in Google Calendar on your phone." */
internal fun skipOneDateLine(service: String): String = "To skip one date, change it in $service on your phone."

/**
 * Handoff 6f: the shared repeat picker worded from the chosen day, a choice the event is too long for locked with its
 * line; then where to skip one date ([service]). Doesn't repeat calls [onPicked]; a repeat doesn't, so the days and Ends
 * can still be set. With no day it asks for one, and its button calls [onChooseDay].
 */
@Composable
internal fun RepeatsPane(form: EditorForm, service: String, onPicked: () -> Unit, onChooseDay: () -> Unit) {
    LaunchedEffect(Unit) { form.openRepeats() }
    val day = form.eventWhen.startDate
    if (day == null) {
        NeedsADay(onChooseDay)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ControlTokens.formGap)) {
        HhRepeatPicker(
            form.pick, day, form.today, form.zone,
            nth = form.nthOn(day),
            lockedLine = { TOO_LONG_TO_REPEAT.takeUnless { _ -> form.fits(it) } },
            daysLocked = form.daysLocked,
            daysLockedLine = TOO_LONG_TO_REPEAT,
            busy = form.saving,
            onPicked = { if (it == RepeatChoice.Never) onPicked() },
        )
        Text(skipOneDateLine(service), style = CalendarType.whoHint, color = Culvery.colors.mute, modifier = Modifier.testTag("repeats_hint"))
    }
}

@Composable
private fun NeedsADay(onChooseDay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ControlTokens.formGap)) {
        Text(CHOOSE_A_DAY_FIRST, style = CalendarType.whoHint, color = Culvery.colors.mute, modifier = Modifier.testTag("repeats_need_day"))
        HhButton(CHOOSE_A_DAY, onChooseDay, Modifier.testTag("repeats_choose_day"), icon = Icons.CALENDAR_MONTH, on = ButtonSurface.Surf)
    }
}
