package uk.co.siland.culvery.capability.holiday.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import java.time.LocalDate
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayRun
import uk.co.siland.culvery.capability.holiday.HolidayWords
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

private const val TAG = "HolidayTile"

/**
 * The tray tile (11 §5.2): the icon and label spoken as one item, "ON" and the back date while it is on, then what
 * didn't happen as [line] under it. A tap does nothing while [running] says a change is out.
 */
@Composable
internal fun HolidayTile(on: Boolean, back: LocalDate?, line: String?, running: HolidayRun?, onTap: () -> Unit) {
    val colors = Culvery.colors
    val ink = if (on) colors.accentInk else colors.ink
    val shape = RoundedCornerShape(HolidayDimens.radius)
    val second = when (running) {
        HolidayRun.TURNING_ON -> HolidayWords.TURNING_ON
        HolidayRun.TURNING_OFF -> HolidayWords.TURNING_OFF
        null -> if (on && back != null) HolidayWords.backShort(back) else null
    }
    val secondTag = if (running != null) "holiday_tile_state" else "holiday_tile_back"
    val spoken = if (running != null) HolidayWords.runningSpoken(running) else HolidayWords.tileSpoken(on, back)
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HolidayDimens.gap),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("holiday_tile")
                .clip(shape)
                .background(if (on) colors.accent else colors.surf2)
                .hhClickable(
                    onTap,
                    label = if (on) HolidayWords.CLICK_OFF else HolidayWords.CLICK_ON,
                    enabled = running == null,
                    spoken = spoken,
                )
                .padding(horizontal = HolidayDimens.paddingH, vertical = HolidayDimens.paddingV),
        ) {
            HhIcon(Icons.BEACH_ACCESS, size = HolidayDimens.icon, filled = true, tint = if (on) ink else colors.accent)
            Column(Modifier.weight(1f).shownNotSpoken()) {
                Text(HolidayWords.LABEL, style = HhType.buttonLabel, color = ink, maxLines = 1)
                if (second != null) {
                    Text(
                        second,
                        style = HolidayDimens.secondStyle,
                        color = if (on) ink else colors.mute,
                        maxLines = 1,
                        modifier = Modifier.testTag(secondTag),
                    )
                }
            }
            if (on) OnPill()
        }
        if (line != null) {
            Text(
                line,
                style = HolidayDimens.secondStyle,
                color = colors.danger,
                modifier = Modifier
                    .padding(start = HolidayDimens.paddingH, end = HolidayDimens.paddingH, top = HolidayDimens.lineGap)
                    .testTag("holiday_tile_line"),
            )
        }
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

/** The tile on Home: a tap asks for a PIN, then opens the "When are you back?" sheet, or turns Holiday mode off. */
@Composable
fun HolidayTileHost(actions: HolidayActions, house: HolidayHouse) {
    val record by house.record.collectAsState()
    val line by house.line.collectAsState()
    val running by house.running.collectAsState()
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Holiday mode tile action failed: ${e::class.simpleName}") }
    HolidayTile(record?.on == true, record?.back, line, running) {
        action.run { actions.toggle { by -> overlay.show { HolidaySheetHost(by, actions, house, overlay::dismiss) } } }
    }
}
