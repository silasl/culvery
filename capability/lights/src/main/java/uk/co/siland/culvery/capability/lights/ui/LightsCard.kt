package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
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
 * The REGULAR Home card (7 §5.1): the main room as a toggle with a › to its tab, All lights off and Holiday mode. With a
 * problem, the §6 line and no controls; before the first read, that it is looking.
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
    val c = Culvery.colors
    HhCard(
        modifier = modifier.fillMaxSize().testTag("lights_card"),
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LightsDimens.buttonGap)) {
            AllOffButton(allOffLit, house.onAllOff, Modifier.weight(1f))
            HolidayButton(holidayOn, run, house.onHoliday)
        }
        if (line != null) {
            Spacer(Modifier.height(LightsDimens.lineGap))
            Text(line, style = HhType.secondary, color = c.danger, maxLines = 2, modifier = Modifier.testTag("lights_card_line"))
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
        )
    }
    val houseActions = remember(actions) { HouseActions(onAllOff = actions::allOff, onHoliday = actions::toggleHoliday) }
    LightsCard(room, holidayOn, allOffLit, run, problem?.let(::problemLine), room?.let { roomLines[it.id] } ?: houseLine, mainRoom, houseActions)
}
