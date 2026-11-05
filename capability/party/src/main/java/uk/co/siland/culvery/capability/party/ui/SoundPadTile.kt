package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.PadTaps
import uk.co.siland.culvery.capability.party.PadUi
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

private const val TAG = "SoundPadTile"
private const val BLOCKED_ALPHA = 0.4f

/**
 * A sound pad in the tray's last row (redesign note 13, 18 §4.1), tagged `sound_pad_{pad}`: the `volume_up` icon in
 * accent over the sound's name on up to two lines, the icon `pause` while it plays (the bundled icon font has no `stop`).
 * While [blocked] it is faded and a tap does nothing, unless it is [playing], when a tap still stops it.
 */
@Composable
internal fun SoundPadTile(pad: PadUi, playing: Boolean, blocked: Boolean, onTap: () -> Unit) {
    val colors = Culvery.colors
    val enabled = !blocked || playing
    Column(
        verticalArrangement = Arrangement.spacedBy(HouseModeDimens.gap, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .testTag("sound_pad_${pad.pad}")
            .alpha(if (enabled) 1f else BLOCKED_ALPHA)
            .clip(RoundedCornerShape(HouseModeDimens.radius))
            .background(colors.surf2)
            .hhClickable(
                onTap,
                label = if (playing) ModesWords.PAD_STOP else ModesWords.PAD_PLAY,
                enabled = enabled,
                spoken = ModesWords.padSpoken(pad.sound.name, playing),
            )
            .padding(horizontal = HouseModeDimens.paddingH, vertical = HouseModeDimens.paddingV),
    ) {
        HhIcon(if (playing) Icons.PAUSE else Icons.VOLUME_UP, size = HouseModeDimens.icon, filled = true, tint = colors.accent)
        Text(
            pad.sound.name,
            style = HouseModeDimens.thirdLabel,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.shownNotSpoken(),
        )
    }
}

/** [pad]'s tile on Home: it shows while its sound plays and fades while the pads are greyed out, and a tap goes to [taps]. */
@Composable
fun SoundPadTileHost(pad: PadUi, taps: PadTaps) {
    val playing by taps.playing.collectAsState()
    val blocked by taps.blocked.collectAsState()
    val action = rememberSingleAction(taps) { e -> Log.w(TAG, "A sound pad's tap failed: ${e::class.simpleName}") }
    SoundPadTile(pad, playing == pad.pad, blocked != null) { action.run { taps.tap(pad) } }
}
