package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.clockText
import uk.co.siland.culvery.display.Screen

/** How long after waking a tap still presses nothing, so a sleepy second tap doesn't reach the screen beneath. */
const val WAKE_GRACE_MS = 600L

const val WAKE_THE_SCREEN = "Wake the screen"
const val SCREEN_ASLEEP = "Screen asleep"
const val THE_CAMERA_IS_ON = "The camera is on"

/**
 * The dark screen while [Screen.ASLEEP], the night clock on [Screen.NIGHT_CLOCK]. Each takes the waking tap, or
 * TalkBack's click, for itself and calls [onWake]: it only wakes the screen, and nothing beneath is pressed or counted as
 * a touch (8 §4.1). For [WAKE_GRACE_MS] after waking a clear layer still takes every tap; after that, nothing.
 * While [cameraRunning] either draws the bare camera glyph centred on [glyphAt], the Watching pill's icon in root
 * coordinates, so the camera stays marked once (9 §6.7); the top right when that isn't known yet.
 */
@Composable
fun SleepLayer(
    screen: Screen,
    time: () -> LocalDateTime,
    onWake: () -> Unit,
    cameraRunning: Boolean = false,
    glyphAt: Offset? = null,
) {
    val asleep = screen != Screen.AWAKE
    var grace by remember { mutableStateOf(false) }
    LaunchedEffect(asleep) {
        if (asleep) {
            grace = true
        } else if (grace) {
            delay(WAKE_GRACE_MS)
            grace = false
        }
    }
    when (screen) {
        Screen.ASLEEP -> WakingLayer(onWake, cameraRunning, glyphAt, name = { contentDescription = SCREEN_ASLEEP })
        Screen.NIGHT_CLOCK -> WakingLayer(onWake, cameraRunning, glyphAt) {
            Text(
                clockText(time().toLocalTime()),
                style = HhType.clock.copy(
                    fontSize = NightClockTokens.size,
                    lineHeight = NightClockTokens.size,
                    letterSpacing = NightClockTokens.tracking,
                    color = NightClockTokens.ink,
                ),
            )
        }
        Screen.AWAKE -> Box(Modifier.sizedWhile { grace }.fillMaxSize().takesTaps {})
    }
}

/** Black over everything beneath, in both themes; its merged node is named by [name] or by its text, and the glyph stays outside it. */
@Composable
private fun WakingLayer(
    onWake: () -> Unit,
    cameraRunning: Boolean,
    glyphAt: Offset?,
    name: SemanticsPropertyReceiver.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val wake by rememberUpdatedState(onWake)
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(
            Modifier
                .fillMaxSize()
                .takesTaps { wake() }
                .semantics(mergeDescendants = true) {
                    name()
                    onClick(WAKE_THE_SCREEN) { wake(); true }
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) { content() }
        if (cameraRunning) CameraGlyph(glyphAt, origin)
    }
}

/** The glyph, centred on [at] (root coordinates) when known; this layer sits inside the system bars' padding, so [origin] is taken off. */
@Composable
private fun BoxScope.CameraGlyph(at: Offset?, origin: Offset) {
    val place = if (at == null) {
        Modifier.align(Alignment.TopEnd).padding(top = DisplayDimens.glyphFallbackTop, end = DisplayDimens.glyphFallbackEnd)
    } else {
        Modifier.offset {
            val half = DisplayDimens.markSize.toPx() / 2
            IntOffset((at.x - origin.x - half).roundToInt(), (at.y - origin.y - half).roundToInt())
        }
    }
    Box(place.size(DisplayDimens.markSize), contentAlignment = Alignment.Center) {
        HhIcon(
            Icons.PHOTO_CAMERA,
            size = DisplayDimens.markSize,
            filled = true,
            tint = NightClockTokens.glyph,
            contentDescription = THE_CAMERA_IS_ON,
        )
    }
}

/** Nothing in size once [on] is false. A touch lays the screen out before it lands, so this ends at once, not at the next frame. */
private fun Modifier.sizedWhile(on: () -> Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(if (on()) constraints else Constraints.fixed(0, 0))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Takes every finger down here before anything else sees it. */
private fun Modifier.takesTaps(onDown: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        onDown()
    }
}

/** The `photo_camera` mark at the top edge's centre, clear of the status bar's items, while the camera runs, on every screen (8 §4.3). Not clickable. */
@Composable
fun CameraMark() {
    Box(Modifier.fillMaxSize().padding(top = DisplayDimens.markTop), contentAlignment = Alignment.TopCenter) {
        HhIcon(Icons.PHOTO_CAMERA, size = DisplayDimens.markSize, tint = Culvery.colors.mute, contentDescription = THE_CAMERA_IS_ON)
    }
}
