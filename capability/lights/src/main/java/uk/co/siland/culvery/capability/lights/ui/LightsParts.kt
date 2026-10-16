package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.lights.ALL_LIGHTS_OFF
import uk.co.siland.culvery.capability.lights.HOLIDAY_MODE
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.TURN_ALL_OFF
import uk.co.siland.culvery.capability.lights.holidayNamed
import uk.co.siland.culvery.capability.lights.holidaySpoken
import uk.co.siland.culvery.capability.lights.holidayState
import uk.co.siland.culvery.capability.lights.openRoomSpoken
import uk.co.siland.culvery.capability.lights.roomSpoken
import uk.co.siland.culvery.capability.lights.roomState
import uk.co.siland.culvery.capability.lights.turnSpoken
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** What All lights off and Holiday mode do, on the card and the tab. */
class HouseActions(val onAllOff: () -> Unit, val onHoliday: () -> Unit)

/** What the Home card's main room does: its toggle, and its › to the tab. */
class MainRoomActions(val onToggle: () -> Unit, val onOpen: () -> Unit)

/** While this shows, the rooms are followed (7 §4.2). */
@Composable
internal fun FollowWhileShown(follow: LightsFollow) {
    DisposableEffect(follow) {
        val stop = follow.watch()
        onDispose { stop() }
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
            .hhClickable(onToggle, label = turnSpoken(room, !room.on), spoken = roomSpoken(room)),
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
            .hhClickable(onOpen, spoken = openRoomSpoken(room)),
    ) {
        HhIcon(Icons.CHEVRON_RIGHT, size = LightsDimens.chevronIcon)
    }
}

@Composable
internal fun AllOffButton(onClick: () -> Unit, modifier: Modifier = Modifier) = HhButton(
    ALL_LIGHTS_OFF, onClick, modifier.testTag("lights_card_all_off").heightIn(min = ButtonTokens.largeHeight),
    icon = Icons.POWER_SETTINGS_NEW, on = ButtonSurface.Surf, compact = true, clickLabel = TURN_ALL_OFF,
)

/** One button for both lines: TalkBack reads "Holiday mode, off" and offers [holidaySpoken] (7 §2.6, §5.4). */
@Composable
internal fun HolidayButton(on: Boolean, run: HouseRun?, onClick: () -> Unit, modifier: Modifier = Modifier) = HhButton(
    HOLIDAY_MODE, onClick, modifier.testTag("lights_card_holiday").heightIn(min = ButtonTokens.largeHeight),
    tone = if (on) ButtonTone.Primary else ButtonTone.Quiet, icon = Icons.LUGGAGE, on = ButtonSurface.Surf,
    spokenName = holidayNamed(on, run), compact = true, clickLabel = holidaySpoken(!on), secondLine = holidayState(on, run),
)

/** The one line saying what is wrong and how to fix it (7 §6). */
@Composable
internal fun LightsStatusLine(text: String) = HhStatusLine(text, "lights_status")
