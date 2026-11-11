package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

/** Sizes and places a node as [area] of the room it is given; [area] of an empty room gives the node's offset. */
private fun Modifier.placedAs(area: Density.(Size) -> Rect): Modifier = this
    .offset { area(Size.Zero).topLeft.round() }
    .layout { measurable, constraints ->
        val rect = area(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
        val width = rect.width.roundToInt().coerceAtLeast(0)
        val height = rect.height.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/** Sizes and places a node as [skyBand] of [measured] inside the room it is given. */
internal fun Modifier.inSkyBand(measured: HomeSkyBand?): Modifier = placedAs { skyBand(measured, it, this) }

/** Home's whole header, from its top-left corner; with nothing measured, all of [size]. */
internal fun headerArea(measured: HomeSkyBand?, size: Size): Rect =
    if (measured == null) Rect(Offset.Zero, size) else Rect(0f, 0f, measured.headerWidth.toFloat(), measured.headerHeight.toFloat())

/** Sizes and places a node as [headerArea] of [measured] inside the room it is given. */
internal fun Modifier.inHomeHeader(measured: HomeSkyBand?): Modifier = placedAs { headerArea(measured, it) }

/**
 * A sky layer: placed by [place] in the room [modifier] gives, silent to TalkBack, clipped, and stepping [elapsed] while
 * [drifting]. [frame] is built once per size and draws the time it is given; the time is read only when drawing.
 */
@Composable
internal fun SkyLayer(
    tag: String,
    drifting: Boolean,
    elapsed: MutableLongState,
    modifier: Modifier,
    place: Modifier.(HomeSkyBand?) -> Modifier,
    frame: CacheDrawScope.(HomeSkyBand?) -> DrawScope.(Long) -> Unit,
) {
    SkyFrames(drifting, elapsed)
    val measured = LocalHomeSkyBand.current
    // The box gives the layer its room; the layer's own node is placed inside it, so the box's size can't stretch it.
    Box(modifier) {
        Spacer(
            Modifier
                .place(measured)
                .testTag(tag)
                .clearAndSetSemantics {}
                .graphicsLayer { clip = true }
                .drawWithCache {
                    val draw = frame(measured)
                    onDrawBehind {
                        if (size.width > 0f && size.height > 0f) draw(elapsed.longValue)
                    }
                },
        )
    }
}

/** Where Birthday's bunting and Christmas's lights hang: [top] below the band's top at both ends, sagging [sag] more at the middle. */
internal object SkyString {
    val top = 6.dp
    val sag = 16.dp
}

/** The height at [x] of the [SkyString] hung across [band]: a parabola through both ends and the middle. */
internal fun hangingY(band: Rect, x: Float, density: Density): Float = with(density) {
    val t = (x - band.left) / band.width
    band.top + SkyString.top.toPx() + SkyString.sag.toPx() * 4f * t * (1f - t)
}

/** The curve [hangingY] gives, as the quadratic through both ends and the middle. */
internal fun hangingPath(band: Rect, density: Density): Path {
    val ends = hangingY(band, band.left, density)
    return Path().apply {
        moveTo(band.left, ends)
        quadraticTo(band.center.x, 2 * hangingY(band, band.center.x, density) - ends, band.right, ends)
    }
}

/** How many things a [pitchPx] apart fit [widthPx]; none for a width narrower than [min] of them. */
internal fun fitCount(widthPx: Float, pitchPx: Float, min: Int): Int =
    if (widthPx < min * pitchPx) 0 else floor(widthPx / pitchPx).toInt()

/** The x of each of a row of things [pitchPx] apart, centred in [band]; none for a band too narrow for [min] of them. */
internal fun hungRow(band: Rect, pitchPx: Float, min: Int): List<Float> {
    val n = fitCount(band.width, pitchPx, min)
    val first = band.left + (band.width - n * pitchPx) / 2 + pitchPx / 2
    return List(n) { i -> first + i * pitchPx }
}
