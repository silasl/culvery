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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlin.math.floor
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

/** Home's whole header, from its top-left corner; with nothing measured, all of [size]. */
fun headerArea(measured: HomeSkyBand?, size: Size): Rect =
    if (measured == null) Rect(Offset.Zero, size) else Rect(0f, 0f, measured.headerWidth.toFloat(), measured.headerHeight.toFloat())

/** Sizes and places a node as [headerArea] of [measured] inside the room it is given. */
internal fun Modifier.inHomeHeader(measured: HomeSkyBand?): Modifier = this
    .offset { headerArea(measured, Size.Zero).topLeft.round() }
    .layout { measurable, constraints ->
        val area = headerArea(measured, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
        val width = area.width.roundToInt().coerceAtLeast(0)
        val height = area.height.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/** The height at [x] of a string hung across [band]: a parabola from [top] below the band's top at both ends to [sag] more at the middle. */
internal fun hangingY(band: Rect, x: Float, top: Dp, sag: Dp, density: Density): Float = with(density) {
    val t = (x - band.left) / band.width
    band.top + top.toPx() + sag.toPx() * 4f * t * (1f - t)
}

/** The curve [hangingY] gives, as the quadratic through both ends and the middle. */
internal fun hangingPath(band: Rect, top: Dp, sag: Dp, density: Density): Path {
    val ends = hangingY(band, band.left, top, sag, density)
    return Path().apply {
        moveTo(band.left, ends)
        quadraticTo(band.center.x, 2 * hangingY(band, band.center.x, top, sag, density) - ends, band.right, ends)
    }
}

/** How many things a [pitchPx] apart fit [widthPx]; none for a width narrower than [min] of them. */
internal fun fitCount(widthPx: Float, pitchPx: Float, min: Int): Int =
    if (widthPx < min * pitchPx) 0 else floor(widthPx / pitchPx).toInt()
