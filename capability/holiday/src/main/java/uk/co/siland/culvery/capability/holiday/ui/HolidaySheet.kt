package uk.co.siland.culvery.capability.holiday.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.holiday.BackDatePick
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayStep
import uk.co.siland.culvery.capability.holiday.HolidayWords
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhDateGrid
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

private const val TAG = "HolidaySheet"
private const val FURTHEST_DAYS = 90L

/**
 * The "When are you back?" sheet (11 §5.2): the date, with the calendar's reason for it, then what Holiday mode will do
 * for [steps]. [choosing] swaps the steps for the month grid, which allows tomorrow to 90 days ahead.
 */
@Composable
internal fun HolidaySheet(
    back: LocalDate,
    today: LocalDate,
    pick: BackDatePick?,
    steps: List<HolidayStep>,
    choosing: Boolean,
    turning: Boolean,
    onChangeDate: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onTurnOn: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Culvery.colors
    var month by remember { mutableStateOf(YearMonth.from(back)) }
    HhSheet(
        padding = PaddingValues(horizontal = HolidayDimens.sheetPaddingH, vertical = HolidayDimens.sheetPaddingV),
        modifier = Modifier.testTag("holiday_sheet").semantics { paneTitle = HolidayWords.WHEN_BACK },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HolidayDimens.buttonGap)) {
            Column(
                Modifier.weight(1f).testTag("holiday_back").semantics(mergeDescendants = true) { contentDescription = HolidayWords.sheetSpoken(back) },
            ) {
                Column(Modifier.shownNotSpoken()) {
                    Text(HolidayWords.WHEN_BACK, style = HhType.sectionTitle, color = c.mute)
                    Text(HolidayWords.backSpoken(back), style = HolidayDimens.dateStyle, color = c.ink)
                }
            }
            HhButton(
                HolidayWords.CHANGE_DATE, onChangeDate, Modifier.testTag("holiday_change_date"),
                tone = ButtonTone.Quiet, enabled = !turning && !choosing, on = ButtonSurface.Bg,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            // A calendar event the date no longer matches is not the reason for it.
            if (pick == null || pick.back == back) {
                Text(HolidayWords.fromCalendar(pick, today), style = HhType.body, color = c.mute, modifier = Modifier.testTag("holiday_from_calendar"))
            }
            if (choosing) {
                HhDateGrid(
                    month = month,
                    onMonth = { month = it },
                    today = today,
                    chosen = setOf(back),
                    band = null,
                    allowed = { it in today.plusDays(1)..today.plusDays(FURTHEST_DAYS) },
                    dots = emptyMap(),
                    spoken = { "" },
                    onPick = onPickDate,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(HolidayWords.WILL, style = HhType.label, color = c.mute)
                Column(verticalArrangement = Arrangement.spacedBy(HolidayDimens.stepGap), modifier = Modifier.testTag("holiday_steps")) {
                    val lines = steps.mapNotNull { HolidayWords.stepLine(it, back) } + HolidayWords.DOOR + HolidayWords.SCREEN
                    lines.forEach { Text(it, style = HhType.body, color = c.ink) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(HolidayDimens.buttonGap)) {
            HhFooterButton(
                if (turning) HolidayWords.TURNING_ON else HolidayWords.TURN_ON_BUTTON,
                onTurnOn, "holiday_turn_on", enabled = !turning,
            )
            HhButton(
                HolidayWords.CANCEL, onCancel, Modifier.fillMaxWidth().testTag("holiday_cancel"),
                tone = ButtonTone.Quiet, size = ButtonSize.Large, enabled = !turning, on = ButtonSurface.Bg,
            )
        }
    }
}

/**
 * Reads the calendar's pick and the steps' readiness as it opens (11 §5.2). Turn on turns Holiday mode on, which carries on
 * if the sheet closes meanwhile, then closes the sheet.
 */
@Composable
internal fun HolidaySheetHost(by: Authorised, actions: HolidayActions, house: HolidayHouse, onClose: () -> Unit) {
    val today = remember { actions.today() }
    var read by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<BackDatePick?>(null) }
    var chosen by remember { mutableStateOf<LocalDate?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(emptyList<HolidayStep>()) }
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Turning Holiday mode on failed: ${e::class.simpleName}") }
    LaunchedEffect(Unit) {
        pick = actions.pick()
        read = true
    }
    if (!read) return
    val back = chosen ?: pick?.back ?: today.plusDays(1)
    LaunchedEffect(back) {
        steps = attempt({ house.plan(back) }) { e ->
            Log.w(TAG, "Couldn't read which steps Holiday mode will take (${e::class.simpleName})")
            emptyList()
        }
    }
    HolidaySheet(
        back, today, pick, steps, choosing, turning = action.busy,
        onChangeDate = { choosing = true },
        onPickDate = {
            if (!action.busy) {
                chosen = it
                choosing = false
            }
        },
        onTurnOn = {
            action.run {
                withContext(NonCancellable) { house.turnOn(back, by) }
                onClose()
            }
        },
        onCancel = onClose,
    )
}
