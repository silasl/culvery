package uk.co.siland.culvery.capability.holiday.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDate
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayRun
import uk.co.siland.culvery.capability.holiday.HolidayWords
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HouseModeTile
import uk.co.siland.culvery.core.ui.HouseModeWords
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

private const val TAG = "HolidayTile"

/**
 * The tray tile (11 §5.2): the icon and label spoken as one item, "ON" and the back date while it is on, then what
 * didn't happen as [line] under it. A tap does nothing while [running] says a change is out.
 */
@Composable
internal fun HolidayTile(on: Boolean, back: LocalDate?, line: String?, running: HolidayRun?, onTap: () -> Unit) {
    val second = when (running) {
        HolidayRun.TURNING_ON -> HouseModeWords.TURNING_ON
        HolidayRun.TURNING_OFF -> HouseModeWords.TURNING_OFF
        null -> if (on && back != null) HolidayWords.backShort(back) else null
    }
    HouseModeTile(
        tag = "holiday_tile",
        icon = Icons.BEACH_ACCESS,
        label = HolidayWords.LABEL,
        on = on,
        second = second,
        secondTag = if (running != null) "holiday_tile_state" else "holiday_tile_back",
        line = line,
        spoken = if (running != null) HolidayWords.runningSpoken(running) else HolidayWords.tileSpoken(on, back),
        enabled = running == null,
        onTap = onTap,
    ) {
        if (on) OnPill()
    }
}

@Composable
private fun OnPill() {
    val colors = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .shownNotSpoken()
            .height(HolidayDimens.pillHeight)
            .background(colors.accentInk, RoundedCornerShape(percent = 50))
            .padding(horizontal = HolidayDimens.pillPaddingH),
    ) {
        Text(HolidayWords.ON, style = HolidayDimens.onStyle, color = colors.accent, maxLines = 1, modifier = Modifier.testTag("holiday_tile_on"))
    }
}

/**
 * The tile on Home: a tap asks for a PIN, then opens the "When are you back?" sheet, or turns Holiday mode off. While
 * Party mode is on it asks nothing and says to turn that off first.
 */
@Composable
fun HolidayTileHost(actions: HolidayActions, house: HolidayHouse) {
    val record by house.record.collectAsState()
    val houseLine by house.line.collectAsState()
    val refusal by actions.refusal.line.collectAsState(initial = actions.refusal.current())
    val running by house.running.collectAsState()
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Holiday mode tile action failed: ${e::class.simpleName}") }
    HolidayTile(record?.on == true, record?.back, refusal ?: houseLine, running) {
        action.run { actions.toggle { by -> overlay.show { HolidaySheetHost(by, actions, house, overlay::dismiss) } } }
    }
}
