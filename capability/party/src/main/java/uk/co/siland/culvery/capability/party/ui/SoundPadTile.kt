package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.PadTaps
import uk.co.siland.culvery.capability.party.PadUi
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.HouseModeThirdTile
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "SoundPadTile"
private const val PULSE_MS = 450

/**
 * A sound pad in the tray's last row (18 §4.1, walk-through), tagged `sound_pad_{pad}`: the pad's number in accent with
 * two sound waves off it, the sound's name spoken but not shown. While it plays the tile is accent-soft and the waves pulse.
 * While [blocked] it is faded and a tap does nothing, unless it is [playing], when a tap still stops it. Until the gate
 * is read ([gateRead]) it is faded too, and a tap still works.
 */
@Composable
internal fun SoundPadTile(pad: PadUi, playing: Boolean, blocked: Boolean, gateRead: Boolean = true, onTap: () -> Unit) {
    val colors = Culvery.colors
    HouseModeThirdTile(
        tag = "sound_pad_${pad.pad}",
        spoken = ModesWords.padSpoken(pad.sound.name, playing),
        clickLabel = if (playing) ModesWords.PAD_STOP else ModesWords.PAD_PLAY,
        enabled = !blocked || playing,
        onTap = onTap,
        background = if (playing) colors.accentSoft else colors.surf2,
        faded = !gateRead,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HouseModeDimens.gap)) {
            Text(pad.pad.toString(), style = HouseModeDimens.thirdNumber, color = colors.accent, maxLines = 1)
            SoundWaves(colors.accent, playing)
        }
    }
}

/** Two arcs, the outer one fainter; while [pulsing] they fade in and out in turn, redrawn without recomposing. */
@Composable
private fun SoundWaves(color: Color, pulsing: Boolean) {
    val pulse = if (pulsing) {
        rememberInfiniteTransition(label = "waves").animateFloat(0f, 1f, infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse), label = "pulse")
    } else {
        null
    }
    Canvas(Modifier.size(HouseModeDimens.thirdWavesWidth, HouseModeDimens.thirdWavesHeight)) {
        val p = pulse?.value ?: 0f
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        fun wave(reach: Float, alpha: Float) {
            val r = size.height / 2 * reach
            drawArc(
                color.copy(alpha = alpha),
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(-r, size.height / 2 - r),
                size = Size(2 * r, 2 * r),
                style = stroke,
            )
        }
        wave(0.45f, 1f - 0.6f * p)
        wave(0.9f, 0.6f + 0.4f * p)
    }
}

/** [pad]'s tile on Home: it shows while its sound plays and fades while the pads are greyed out, and a tap goes to [taps]. */
@Composable
fun SoundPadTileHost(pad: PadUi, taps: PadTaps) {
    val playing by taps.playing.collectAsState()
    val blocked by taps.blocked.collectAsState()
    val gateRead by taps.gateRead.collectAsState()
    val action = rememberSingleAction(taps) { e -> Log.w(TAG, "A sound pad's tap failed: ${e::class.simpleName}") }
    SoundPadTile(pad, playing == pad.pad, blocked != null, gateRead) { action.run { taps.tap(pad) } }
}
