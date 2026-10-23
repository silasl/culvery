package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LIGHTS
import uk.co.siland.culvery.capability.lights.LIGHTS_ID
import uk.co.siland.culvery.capability.lights.LOOKING_FOR_LIGHTS
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens

/**
 * The REGULAR Home card (9 §4.2): the main room as a toggle with a › to its tab, its brightness, All lights off and Holiday mode. With a
 * problem, the §6 line and no controls; before the first read, that it is looking. In the side-by-side row (10 §4.9) a line
 * under the buttons takes the two gaps and shows on one line, ending in … if longer.
 */
@Composable
fun LightsCard(
    main: LightRoom?,
    holidayOn: Boolean,
    allOffLit: Boolean,
    run: HouseRun?,
    status: String?,
    line: String?,
    mainRoom: MainRoomActions,
    house: HouseActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val squeezed = line != null && maxHeight <= LightsDimens.rowCardMaxHeight
        LightsCardContent(main, holidayOn, allOffLit, run, status, line, squeezed, mainRoom, house)
    }
}

@Composable
private fun LightsCardContent(
    main: LightRoom?,
    holidayOn: Boolean,
    allOffLit: Boolean,
    run: HouseRun?,
    status: String?,
    line: String?,
    squeezed: Boolean,
    mainRoom: MainRoomActions,
    house: HouseActions,
) {
    val c = Culvery.colors
    HhCard(
        modifier = Modifier.fillMaxSize().testTag("lights_card"),
        radius = ShellTokens.homeCardRadius,
        padding = PaddingValues(horizontal = LightsDimens.cardPaddingH, vertical = LightsDimens.cardPaddingV),
    ) {
        if (status != null || main == null) {
            Text(LIGHTS, style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(LightsDimens.lineGap))
            if (status != null) LightsStatusLine(status) else Text(LOOKING_FOR_LIGHTS, style = HhType.body, color = c.mute)
            return@HhCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LightsDimens.blockGap)) {
            MainRoomButton(main, mainRoom.onToggle, Modifier.weight(1f))
            OpenRoomButton(main, mainRoom.onOpen)
        }
        Spacer(Modifier.weight(1f))
        if (main.dimmable) {
            BrightnessRow(main, "lights_card_slider", mainRoom.onDrag, mainRoom.onLetGo)
            if (!squeezed) Spacer(Modifier.height(LightsDimens.bottomGap))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LightsDimens.buttonGap)) {
            AllOffButton(allOffLit, house.onAllOff, Modifier.weight(1f))
            HolidayButton(holidayOn, run, house.onHoliday)
        }
        if (line != null) {
            if (!squeezed) Spacer(Modifier.height(LightsDimens.lineGap))
            Text(
                line, style = HhType.secondary, color = c.danger, maxLines = if (squeezed) 1 else 2, overflow = if (squeezed) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier.testTag("lights_card_line"),
            )
        }
    }
}

/** The card over the view; while it shows, the rooms are followed (7 §4.2). */
@Composable
internal fun LightsCardHost(view: LightsView, actions: LightsActions, house: LightsHouse, follow: LightsFollow) {
    FollowWhileShown(follow)
    val navigator = LocalShellNavigator.current
    val main by view.main.collectAsStateWithLifecycle()
    val holidayOn by view.holidayOn.collectAsStateWithLifecycle(false)
    val allOffLit by view.allOffLit.collectAsStateWithLifecycle(false)
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val run by house.running.collectAsStateWithLifecycle()
    val roomLines by actions.lines.collectAsStateWithLifecycle()
    val houseLine by actions.houseLine.collectAsStateWithLifecycle()
    val room = main
    val mainRoom = remember(room, actions, view, navigator) {
        MainRoomActions(
            onToggle = { room?.let(actions::toggle) },
            onOpen = {
                room?.let { view.focus.value = it.id }
                navigator.openTab(LIGHTS_ID)
            },
            onDrag = { percent -> room?.let { actions.dragBrightness(it, percent) } },
            onLetGo = { percent -> room?.let { actions.letGoBrightness(it, percent) } },
        )
    }
    val houseActions = remember(actions) { HouseActions(onAllOff = actions::allOff, onHoliday = actions::toggleHoliday) }
    LightsCard(room, holidayOn, allOffLit, run, problem?.let(::problemLine), room?.let { roomLines[it.id] } ?: houseLine, mainRoom, houseActions)
}
