package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.lights.ALL_LIGHTS_OFF
import uk.co.siland.culvery.capability.lights.HOLIDAY_MODE
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.TURN_ALL_OFF
import uk.co.siland.culvery.capability.lights.holidayNamed
import uk.co.siland.culvery.capability.lights.holidaySpoken
import uk.co.siland.culvery.capability.lights.holidayState
import uk.co.siland.culvery.capability.lights.openRoomSpoken
import uk.co.siland.culvery.capability.lights.roomSpoken
import uk.co.siland.culvery.capability.lights.roomState
import uk.co.siland.culvery.capability.lights.turnSpoken
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** What the Home card's controls do. */
class HouseActions(
    val onToggle: () -> Unit,
    val onOpen: () -> Unit,
    val onAllOff: () -> Unit,
    val onHoliday: () -> Unit,
) {
    companion object {
        /** For screenshots: nothing happens. */
        val None = HouseActions({}, {}, {}, {})
    }
}

/** The main room as one button: TalkBack reads [roomSpoken] and offers [turnSpoken] (7 §2.3, §5.4). */
@Composable
internal fun MainRoomButton(room: LightRoom, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LightsDimens.blockGap),
        modifier = modifier
            .testTag("lights_card_main")
            .heightIn(min = LightsDimens.chevron)
            .semantics { contentDescription = roomSpoken(room) }
            .hhClickable(onToggle, label = turnSpoken(room, !room.on)),
    ) {
        HhIcon(Icons.LIGHTBULB, size = LightsDimens.bulb, filled = room.on, tint = if (room.on) c.accent else c.mute)
        Column(Modifier.weight(1f).shownNotSpoken()) {
            Text(room.name, style = HhType.cardTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(roomState(room), style = HhType.body, color = c.mute, maxLines = 1)
        }
    }
}

/** The › beside the main room: opens that room on the Lights tab. */
@Composable
internal fun OpenRoomButton(room: LightRoom, onOpen: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("lights_card_open")
            .size(LightsDimens.chevron)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Surf, Culvery.colors))
            .semantics { contentDescription = openRoomSpoken(room) }
            .hhClickable(onOpen),
    ) {
        HhIcon(Icons.CHEVRON_RIGHT, size = LightsDimens.chevronIcon)
    }
}

/** A pill like HhButton whose spoken words come from a click label, so they need not start with its text. */
@Composable
private fun HouseButton(
    icon: String,
    primary: Boolean,
    onClick: () -> Unit,
    clickLabel: String,
    modifier: Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LightsDimens.buttonIconGap),
        modifier = modifier
            .heightIn(min = LightsDimens.buttonHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (primary) c.accent else quietFill(ButtonSurface.Surf, c))
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .hhClickable(onClick, label = clickLabel)
            .padding(start = LightsDimens.buttonPaddingStart, end = LightsDimens.buttonPaddingEnd),
    ) {
        HhIcon(icon, size = LightsDimens.buttonIcon, tint = if (primary) c.accentInk else c.ink)
        content()
    }
}

@Composable
internal fun AllOffButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    HouseButton(Icons.POWER_SETTINGS_NEW, false, onClick, TURN_ALL_OFF, modifier.testTag("lights_card_all_off")) {
        Text(ALL_LIGHTS_OFF, style = HhType.buttonLabel, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** One button for both lines: TalkBack reads "Holiday mode, off" and offers [holidaySpoken] (7 §2.6, §5.4). */
@Composable
internal fun HolidayButton(on: Boolean, run: HouseRun?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val ink = if (on) c.accentInk else c.ink
    HouseButton(
        Icons.LUGGAGE, on, onClick, holidaySpoken(!on), modifier.testTag("lights_card_holiday"),
        description = holidayNamed(on, run),
    ) {
        Column(Modifier.shownNotSpoken()) {
            Text(HOLIDAY_MODE, style = HhType.buttonLabel, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(holidayState(on, run), style = HhType.secondary, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The one line saying what is wrong and how to fix it (7 §6). */
@Composable
internal fun LightsStatusLine(text: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LightsDimens.statusGap),
        modifier = Modifier.testTag("lights_status").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        HhIcon(Icons.INFO, size = LightsDimens.statusIcon, tint = c.danger)
        Text(text, style = HhType.body, color = c.danger)
    }
}
