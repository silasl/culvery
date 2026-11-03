package uk.co.siland.culvery.capability.holiday.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayRun
import uk.co.siland.culvery.capability.holiday.HolidayWords
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.HouseModeOnPill
import uk.co.siland.culvery.core.ui.HouseModeTile
import uk.co.siland.culvery.core.ui.HouseModeWords
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "HolidayTile"

/**
 * The tray tile (11 §5.2): the icon and label spoken as one item, and "ON" and the back date while it is on. A tap does
 * nothing while [running] says a change is out.
 */
@Composable
internal fun HolidayTile(on: Boolean, back: LocalDate?, running: HolidayRun?, onTap: () -> Unit) {
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
        spoken = if (running != null) HolidayWords.runningSpoken(running) else HolidayWords.tileSpoken(on, back),
        enabled = running == null,
        onTap = onTap,
    ) {
        if (on) HouseModeOnPill("holiday_tile_on")
    }
}

/**
 * The tile on Home: a tap asks for a PIN, then opens the "When are you back?" sheet, or turns Holiday mode off. While
 * Party mode is on it asks nothing and says to turn that off first.
 */
@Composable
fun HolidayTileHost(actions: HolidayActions, house: HolidayHouse) {
    val record by house.record.collectAsState()
    val running by house.running.collectAsState()
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Holiday mode tile action failed: ${e::class.simpleName}") }
    HolidayTile(record?.on == true, record?.back, running) {
        action.run { actions.toggle { by -> overlay.show { HolidaySheetHost(by, actions, house, overlay::dismiss) } } }
    }
}

/** The tile's line under its tray row: why a tap was refused, else what didn't happen. */
fun holidayTileLine(actions: HolidayActions, house: HolidayHouse): Flow<String?> =
    combine(actions.refusal.line, house.line) { refusal, houseLine -> refusal ?: houseLine }

/** The tag the tile's line has under its tray row. */
const val HOLIDAY_TILE_LINE_TAG = "holiday_tile_line"
