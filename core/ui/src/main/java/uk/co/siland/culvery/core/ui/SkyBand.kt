package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The sky moves at most this often, about 15 frames a second. */
internal const val SKY_FRAME_MS = 66L

/**
 * The open band in Home's header where the sky is drawn: right of the clock and date, left of the header items, from Home's
 * top to the header's bottom. With nothing measured the band is all of [size] (14 §4.3).
 */
fun skyBand(measured: HomeSkyBand?, size: Size, density: Density): Rect {
    if (measured == null) return Rect(Offset.Zero, size)
    val gap = with(density) { HalloweenDimens.skyGap.toPx() }
    val right = if (measured.itemsWidth > 0) measured.headerWidth - measured.itemsWidth - gap else measured.headerWidth.toFloat()
    return Rect(maxOf(measured.clockWidth, measured.dateWidth, measured.greetingWidth) + gap, 0f, right, measured.headerHeight.toFloat())
}

/** While [drifting], adds the time since the last step to [elapsed] about every [SKY_FRAME_MS]; still, it runs no frames at all. */
@Composable
internal fun SkyFrames(drifting: Boolean, elapsed: MutableLongState) {
    LaunchedEffect(drifting) {
        if (!drifting) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last >= 0) elapsed.longValue += now - last
                last = now
            }
            delay(SKY_FRAME_MS)
        }
    }
}

/** Sizes and places a node as [skyBand] of [measured] inside the room it is given. */
internal fun Modifier.inSkyBand(measured: HomeSkyBand?): Modifier = this
    .offset { measured?.let { skyBand(it, Size.Zero, this).topLeft.round() } ?: IntOffset.Zero }
    .layout { measurable, constraints ->
        val band = skyBand(measured, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), this)
        val width = band.width.roundToInt().coerceAtLeast(0)
        val height = band.height.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
