package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.lights.LIGHTS
import uk.co.siland.culvery.capability.lights.LIGHTS_ID
import uk.co.siland.culvery.capability.lights.LOOKING_FOR_LIGHTS
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.NEXT_SCENE
import uk.co.siland.culvery.capability.lights.SCENE
import uk.co.siland.culvery.capability.lights.currentSceneName
import uk.co.siland.culvery.capability.lights.nextScene
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.scenePillSpoken
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.shownNotSpoken

/**
 * The REGULAR Home card (9 §4.2): the main room as a toggle with a › to its tab, its brightness, All lights off and a
 * Scene pill that steps to its next scene (15a §3). With a problem, the §6 line and no controls; before the first read,
 * that it is looking. In the side-by-side row (10 §4.9) a line under the buttons takes the two gaps and shows on one
 * line, ending in … if longer.
 */
@Composable
fun LightsCard(
    main: LightRoom?,
    allOffLit: Boolean,
    status: String?,
    line: String?,
    mainRoom: MainRoomActions,
    onAllOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val squeezed = line != null && maxHeight <= LightsDimens.rowCardMaxHeight
        LightsCardContent(main, allOffLit, status, line, squeezed, mainRoom, onAllOff)
    }
}

@Composable
private fun LightsCardContent(
    main: LightRoom?,
    allOffLit: Boolean,
    status: String?,
    line: String?,
    squeezed: Boolean,
    mainRoom: MainRoomActions,
    onAllOff: () -> Unit,
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
            AllOffButton(allOffLit, onAllOff, Modifier.weight(1f))
            if (main.scenes.isNotEmpty()) ScenePill(main) { nextScene(main)?.let(mainRoom.onScene) }
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

/** "Scene" with a chip holding the room's current scene; a tap recalls the next one (15a §3). Spoken "Scene, Relax". */
@Composable
private fun ScenePill(room: LightRoom, onNext: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LightsDimens.scenePillGap),
        modifier = Modifier
            .testTag("lights_card_scene")
            .heightIn(min = LightsDimens.scenePillHeight)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Surf, c))
            .hhClickable(onNext, label = NEXT_SCENE, spoken = scenePillSpoken(room))
            .padding(LightsDimens.scenePillPadding),
    ) {
        Text(SCENE, style = HhType.buttonLabel, color = c.ink, maxLines = 1, modifier = Modifier.shownNotSpoken())
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(LightsDimens.sceneChipHeight)
                .background(c.surf3, CircleShape)
                .padding(horizontal = LightsDimens.sceneChipPaddingH)
                .shownNotSpoken(),
        ) {
            Text(currentSceneName(room), style = HhType.buttonLabel, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = LightsDimens.sceneChipMaxWidth))
        }
    }
}

/** The card over the view; while it shows, the rooms are followed (7 §4.2). */
@Composable
internal fun LightsCardHost(view: LightsView, actions: LightsActions, follow: LightsFollow) {
    FollowWhileShown(follow)
    val navigator = LocalShellNavigator.current
    val main by view.main.collectAsStateWithLifecycle()
    val allOffLit by view.allOffLit.collectAsStateWithLifecycle(false)
    val problem by view.problem.collectAsStateWithLifecycle(null)
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
            onScene = { scene -> room?.let { actions.recallScene(it, scene) } },
            onDrag = { percent -> room?.let { actions.dragBrightness(it, percent) } },
            onLetGo = { percent -> room?.let { actions.letGoBrightness(it, percent) } },
        )
    }
    LightsCard(room, allOffLit, problem?.let(::problemLine), room?.let { roomLines[it.id] } ?: houseLine, mainRoom, actions::allOff)
}
