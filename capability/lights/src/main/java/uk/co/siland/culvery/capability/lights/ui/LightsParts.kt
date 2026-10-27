package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.capability.lights.ALL_LIGHTS_OFF
import uk.co.siland.culvery.capability.lights.HOLIDAY_LIGHTS
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.OFF
import uk.co.siland.culvery.capability.lights.TURN_ALL_OFF
import uk.co.siland.culvery.capability.lights.brightnessName
import uk.co.siland.culvery.capability.lights.cardStatus
import uk.co.siland.culvery.capability.lights.holidayNamed
import uk.co.siland.culvery.capability.lights.holidaySpoken
import uk.co.siland.culvery.capability.lights.holidayState
import uk.co.siland.culvery.capability.lights.openRoomSpoken
import uk.co.siland.culvery.capability.lights.roomSpoken
import uk.co.siland.culvery.capability.lights.turnSpoken
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPercentSlider
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** What All lights off and Holiday lights do, on the card and the tab. */
class HouseActions(val onAllOff: () -> Unit, val onHoliday: () -> Unit)

/** What the Home card's main room does: its toggle, its › to the tab, and its slider while moving and let go. */
class MainRoomActions(val onToggle: () -> Unit, val onOpen: () -> Unit, val onDrag: (Int) -> Unit = {}, val onLetGo: (Int) -> Unit = {})

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
            Text(room.name, style = HhType.cardTitle.copy(fontSize = LightsDimens.nameSize), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cardStatus(room), style = HhType.body.copy(fontSize = LightsDimens.statusSize), color = c.mute, maxLines = 1)
        }
    }
}

/** The longest a Lights slider stays where it was let go while the bridge has yet to report that level (a refused command shows after it). */
internal const val LIGHTS_HOLD_MS = 5_000L

/**
 * A room's brightness between the dim and bright icons: disabled at its remembered level, reading "Off", while the room is
 * off (9 §2.5). [showPercent] adds the level after the bright icon.
 */
@Composable
internal fun BrightnessRow(
    room: LightRoom,
    tag: String,
    onDrag: (Int) -> Unit,
    onLetGo: (Int) -> Unit,
    showPercent: Boolean = false,
    trackHeight: Dp = LightsDimens.trackHeight,
) {
    val c = Culvery.colors
    val level = room.brightness.coerceIn(1, 100)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LightsDimens.blockGap)) {
        HhIcon(Icons.BRIGHTNESS_LOW, size = LightsDimens.sliderIcon, tint = c.mute, modifier = Modifier.shownNotSpoken())
        HhPercentSlider(
            room.name, level, tag, Modifier.weight(1f),
            range = 1..100, spokenName = brightnessName(room), spokenState = { if (room.on) "$it percent" else OFF },
            onDrag = onDrag, enabled = room.on, trackHeight = trackHeight, holdCeilingMs = LIGHTS_HOLD_MS, onLetGo = onLetGo,
        )
        HhIcon(Icons.BRIGHTNESS_HIGH, size = LightsDimens.sliderIcon, tint = c.mute, modifier = Modifier.shownNotSpoken())
        if (showPercent) {
            Text(
                "$level%",
                style = HhType.secondary.copy(fontSize = LightsDimens.percentSize, fontWeight = FontWeight.W600),
                color = if (room.on) c.ink else c.mute,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = LightsDimens.percentWidth).shownNotSpoken(),
            )
        }
    }
}

/** The › beside the main room: opens that room on the Lights tab. */
@Composable
internal fun OpenRoomButton(room: LightRoom, onOpen: () -> Unit) {
    HhRoundButton(Icons.CHEVRON_RIGHT, openRoomSpoken(room), onOpen, Modifier.testTag("lights_card_open"), on = ButtonSurface.Surf)
}

/** Accent while a room it would turn off is on; otherwise the shared disabled button, which does nothing. */
@Composable
internal fun AllOffButton(
    lit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "lights_card_all_off",
    surface: ButtonSurface = ButtonSurface.Surf,
) = HhButton(
    ALL_LIGHTS_OFF, onClick, modifier.testTag(tag),
    tone = ButtonTone.Primary, enabled = lit, icon = Icons.POWER_SETTINGS_NEW, on = surface, compact = true, clickLabel = TURN_ALL_OFF,
)

/** One button for both lines: TalkBack reads "Holiday lights, off" and offers [holidaySpoken] (7 §2.6, §5.4). */
@Composable
internal fun HolidayButton(
    on: Boolean,
    run: HouseRun?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "lights_card_holiday",
    surface: ButtonSurface = ButtonSurface.Surf,
) = HhButton(
    HOLIDAY_LIGHTS, onClick, modifier.testTag(tag),
    tone = if (on) ButtonTone.Primary else ButtonTone.Quiet, icon = Icons.LUGGAGE, on = surface,
    spokenName = holidayNamed(on, run), compact = true, clickLabel = holidaySpoken(!on), secondLine = holidayState(on, run),
)

/** The one line saying what is wrong and how to fix it (7 §6). */
@Composable
internal fun LightsStatusLine(text: String) = HhStatusLine(text, "lights_status")
