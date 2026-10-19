package uk.co.siland.culvery.display.camera

import kotlin.math.abs

internal const val DIFF_WIDTH = 32
internal const val DIFF_HEIGHT = 24

/** How far a pixel must change, beyond the whole frame's change, to count (8 §4.3; provisional). */
internal const val PIXEL_DELTA = 24

/** The share of pixels that must count for the frame to be movement (provisional). */
internal const val CHANGED_SHARE = 0.04f

/** A small greyscale frame; each byte is a level from 0 to 255, read `and 0xFF`. */
internal class Luma(val width: Int, val height: Int, val values: ByteArray)

private fun Luma.level(i: Int): Int = values[i].toInt() and 0xFF

private fun Luma.mean(): Float = values.indices.sumOf { level(it) }.toFloat() / values.size

/** Whether something moved between one looked-at frame and the next, ignoring the whole frame getting lighter or darker. */
internal class FrameDiff {
    private var last: Luma? = null

    fun moved(frame: Luma): Boolean {
        val before = last
        last = frame
        if (before == null || before.width != frame.width || before.height != frame.height) return false
        val shift = frame.mean() - before.mean()
        val changed = frame.values.indices.count { abs(frame.level(it) - before.level(it) - shift) > PIXEL_DELTA }
        return changed >= CHANGED_SHARE * frame.values.size
    }

    fun reset() {
        last = null
    }
}
