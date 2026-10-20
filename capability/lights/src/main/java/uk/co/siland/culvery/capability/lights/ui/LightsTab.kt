package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LIGHTS
import uk.co.siland.culvery.capability.lights.LOOKING_FOR_LIGHTS
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.roomsOn
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhTray
import uk.co.siland.culvery.core.ui.HhType

/** What a room's card does: its switch, its slider while moving and let go, and its scene chips. */
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
 * The Lights tab (7 §5.2, 9 §4.4): the title with the rooms on and a tray of All lights off and Holiday mode, then the
 * rooms' cards in [LightsGrid]. The status line shows only when something is wrong; [houseLine] is how the last All
 * lights off or Holiday mode went and [lines] each room's last refused command. A [focus] naming a listed room is
 * brought into view, then handed back through [onFocused].
 */
@Composable
fun LightsTab(
    rooms: List<LightRoom>,
    holidayOn: Boolean,
    allOffLit: Boolean,
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
    val looking = rooms.isEmpty() && status == null
    // HhPageHeader ends with its gap; a line under it needs one of its own before the rooms.
    val below = if (status != null || houseLine != null || looking) LightsDimens.gridGap else 0.dp
    LightsGrid(rooms, lines, room, focus, onFocused, modifier = modifier.testTag("lights_tab"), header = {
        Column(Modifier.padding(bottom = below), verticalArrangement = Arrangement.spacedBy(LightsDimens.tabGap)) {
            HhPageHeader(
                LIGHTS, if (rooms.isEmpty()) null else roomsOn(rooms),
                trailing = {
                    HhTray {
                        AllOffButton(allOffLit, house.onAllOff, tag = "lights_tray_all_off", surface = ButtonSurface.Bg)
                        HolidayButton(holidayOn, run, house.onHoliday, tag = "lights_tray_holiday", surface = ButtonSurface.Bg)
                    }
                },
            )
            if (status != null) LightsStatusLine(status)
            if (houseLine != null) Text(houseLine, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("lights_tab_line"))
            if (looking) Text(LOOKING_FOR_LIGHTS, style = HhType.body, color = c.mute)
        }
    })
}

/** The tab over the view; while it shows, the rooms are followed (7 §4.2). */
@Composable
internal fun LightsTabHost(view: LightsView, actions: LightsActions, house: LightsHouse, follow: LightsFollow) {
    FollowWhileShown(follow)
    val rooms by view.rooms.collectAsStateWithLifecycle(emptyList())
    val holidayOn by view.holidayOn.collectAsStateWithLifecycle(false)
    val allOffLit by view.allOffLit.collectAsStateWithLifecycle(false)
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val focus by view.focus.collectAsStateWithLifecycle()
    val run by house.running.collectAsStateWithLifecycle()
    val lines by actions.lines.collectAsStateWithLifecycle()
    val houseLine by actions.houseLine.collectAsStateWithLifecycle()
    val houseActions = remember(actions) { HouseActions(onAllOff = actions::allOff, onHoliday = actions::toggleHoliday) }
    val roomActions = remember(actions) { RoomActions(actions::setOn, actions::dragBrightness, actions::letGoBrightness, actions::recallScene) }
    LightsTab(
        rooms, holidayOn, allOffLit, run, problem?.let(::problemLine), houseLine, lines, focus,
        onFocused = { view.focus.value = null }, houseActions, roomActions,
    )
}
