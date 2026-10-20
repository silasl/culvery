package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.cardStatus
import uk.co.siland.culvery.capability.lights.roomSpoken
import uk.co.siland.culvery.capability.lights.sceneSpoken
import uk.co.siland.culvery.capability.lights.turnSpoken
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.shownNotSpoken

/**
 * One room (9 §4.4): its bulb, name, [cardStatus] and switch; its brightness with the level; and its scenes in one row
 * that scrolls sideways, so a card of fixed height never cuts them. An off room has a disabled slider, and a scene chip turns
 * it on with that scene. [line] is the room's last refused command.
 */
@Composable
internal fun LightsRoomCard(room: LightRoom, line: String?, actions: RoomActions, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val tag = "lights_room_${room.id}"
    HhCard(
        modifier = modifier.fillMaxWidth().testTag(tag),
        radius = LightsDimens.roomRadius,
        padding = PaddingValues(horizontal = LightsDimens.cardPaddingH, vertical = LightsDimens.cardPaddingV),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LightsDimens.roomInnerGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LightsDimens.blockGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LightsDimens.chevron)
                    .testTag("${tag}_switch")
                    .hhClickable({ actions.onOn(room, !room.on) }, label = turnSpoken(room, !room.on), role = Role.Switch, spoken = roomSpoken(room)),
            ) {
                HhIcon(Icons.LIGHTBULB, size = LightsDimens.roomBulb, filled = room.on, tint = if (room.on) c.accent else c.mute)
                Column(Modifier.weight(1f).shownNotSpoken()) {
                    Text(
                        room.name, style = HhType.cardTitle.copy(fontSize = LightsDimens.roomNameSize), color = c.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(cardStatus(room), style = HhType.body.copy(fontSize = LightsDimens.statusSize), color = c.mute, maxLines = 1)
                }
                HhSwitch(room.on, onCheckedChange = null, tag = "${tag}_toggle")
            }
            if (room.dimmable) {
                BrightnessRow(
                    room, "${tag}_brightness", { actions.onDrag(room, it) }, { actions.onLetGo(room, it) },
                    showPercent = true, trackHeight = LightsDimens.roomTrack,
                )
            }
            if (room.scenes.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(LightsDimens.chipGap),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().selectableGroup().testTag("${tag}_scenes"),
                ) {
                    items(room.scenes, key = { it.id }) { scene ->
                        HhChoiceChip(
                            scene.name, selected = scene.id == room.activeSceneId, tag = "${tag}_scene_${scene.id}",
                            onClick = { actions.onScene(room, scene) }, role = Role.RadioButton, spokenName = sceneSpoken(scene, room),
                            unselectedFill = c.surf2,
                        )
                    }
                }
            }
            if (line != null) Text(line, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("${tag}_line"))
        }
    }
}
