package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.lights.LOOKING_FOR_LIGHTS
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.brightnessName
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.roomSpoken
import uk.co.siland.culvery.capability.lights.roomState
import uk.co.siland.culvery.capability.lights.sceneSpoken
import uk.co.siland.culvery.capability.lights.turnSpoken
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPercentSlider
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** What a room's row does: its switch, its slider while moving and let go, and its scene chips. */
class RoomActions(
    val onOn: (LightRoom, Boolean) -> Unit,
    val onDrag: (LightRoom, Int) -> Unit,
    val onLetGo: (LightRoom, Int) -> Unit,
    val onScene: (LightRoom, LightScene) -> Unit,
) {
    companion object {
        /** For screenshots: nothing happens. */
        val None = RoomActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> })
    }
}

/**
 * The Lights tab (7 §5.2): All lights off and Holiday mode, then one row per Hue room in Hue's order. The status line
 * shows only when something is wrong; [houseLine] is how the last All lights off or Holiday mode went and [lines] each
 * room's last refused command. A [focus] naming a listed room is scrolled to, then handed back through [onFocused].
 */
@Composable
fun LightsTab(
    rooms: List<LightRoom>,
    holidayOn: Boolean,
    run: HouseRun?,
    status: String?,
    houseLine: String?,
    lines: Map<String, String>,
    focus: String?,
    onFocused: () -> Unit,
    house: HouseActions,
    room: RoomActions,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val list = rememberLazyListState()
    LaunchedEffect(focus, rooms) {
        if (focus == null) return@LaunchedEffect
        val at = rooms.indexOfFirst { it.id == focus }
        // The top block is item 0; before the first read there is no room to name, so the focus waits.
        if (at >= 0) list.scrollToItem(at + 1)
        if (at >= 0 || rooms.isNotEmpty()) onFocused()
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(LightsDimens.tabGap),
        modifier = modifier.fillMaxSize().testTag("lights_tab"),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(LightsDimens.tabGap)) {
                if (status != null) LightsStatusLine(status)
                Row(horizontalArrangement = Arrangement.spacedBy(LightsDimens.buttonGap)) {
                    AllOffButton(house.onAllOff)
                    HolidayButton(holidayOn, run, house.onHoliday)
                }
                if (houseLine != null) Text(houseLine, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("lights_tab_line"))
                if (rooms.isEmpty() && status == null) Text(LOOKING_FOR_LIGHTS, style = HhType.body, color = c.mute)
            }
        }
        items(rooms, key = { it.id }) { RoomRow(it, lines[it.id], room) }
    }
}

@Composable
private fun RoomRow(room: LightRoom, line: String?, actions: RoomActions) {
    val c = Culvery.colors
    val tag = "lights_row_${room.id}"
    HhCard(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        radius = ListRowTokens.radius,
        padding = PaddingValues(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LightsDimens.rowInnerGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LightsDimens.blockGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LightsDimens.chevron)
                    .testTag("${tag}_switch")
                    .semantics { contentDescription = roomSpoken(room) }
                    .hhClickable({ actions.onOn(room, !room.on) }, label = turnSpoken(room, !room.on), role = Role.Switch),
            ) {
                HhIcon(Icons.LIGHTBULB, size = LightsDimens.bulb, filled = room.on, tint = if (room.on) c.accent else c.mute)
                Column(Modifier.weight(1f).shownNotSpoken()) {
                    Text(room.name, style = HhType.cardTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(roomState(room), style = HhType.body, color = c.mute, maxLines = 1)
                }
                HhSwitch(room.on, onCheckedChange = null, tag = "${tag}_toggle")
            }
            if (room.dimmable) {
                HhPercentSlider(
                    room.name, room.brightness.coerceIn(1, 100), "${tag}_brightness", Modifier.fillMaxWidth(),
                    range = 1..100, spokenName = brightnessName(room),
                    onDrag = { actions.onDrag(room, it) },
                ) { actions.onLetGo(room, it) }
            }
            if (room.scenes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(LightsDimens.chipGap),
                    verticalArrangement = Arrangement.spacedBy(LightsDimens.chipGap),
                    modifier = Modifier.fillMaxWidth().selectableGroup().testTag("${tag}_scenes"),
                ) {
                    room.scenes.forEach { scene ->
                        HhChoiceChip(
                            scene.name, selected = scene.id == room.activeSceneId, tag = "${tag}_scene_${scene.id}",
                            onClick = { actions.onScene(room, scene) }, role = Role.RadioButton, spokenName = sceneSpoken(scene, room),
                        )
                    }
                }
            }
            if (line != null) Text(line, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("${tag}_line"))
        }
    }
}

/** The tab over the view; while it shows, the rooms are followed (7 §4.2). */
@Composable
internal fun LightsTabHost(view: LightsView, actions: LightsActions, house: LightsHouse, follow: LightsFollow) {
    FollowWhileShown(follow)
    val rooms by view.rooms.collectAsStateWithLifecycle(emptyList())
    val holidayOn by view.holidayOn.collectAsStateWithLifecycle(false)
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val focus by view.focus.collectAsStateWithLifecycle()
    val run by house.running.collectAsStateWithLifecycle()
    val lines by actions.lines.collectAsStateWithLifecycle()
    val houseLine by actions.houseLine.collectAsStateWithLifecycle()
    val houseActions = remember(actions) { HouseActions(onAllOff = actions::allOff, onHoliday = actions::toggleHoliday) }
    val roomActions = remember(actions) { RoomActions(actions::setOn, actions::dragBrightness, actions::letGoBrightness, actions::recallScene) }
    LightsTab(
        rooms, holidayOn, run, problem?.let(::problemLine), houseLine, lines, focus,
        onFocused = { view.focus.value = null }, houseActions, roomActions,
    )
}
