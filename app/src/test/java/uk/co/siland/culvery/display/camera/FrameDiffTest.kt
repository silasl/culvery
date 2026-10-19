package uk.co.siland.culvery.display.camera

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

private const val BACKGROUND = 60
private const val SHAPE = 150

/** A 32 × 24 frame whose pixels are [value] clamped to 0–255. */
internal fun frame(value: (x: Int, y: Int) -> Int): Luma {
    val values = ByteArray(DIFF_WIDTH * DIFF_HEIGHT)
    for (y in 0 until DIFF_HEIGHT) for (x in 0 until DIFF_WIDTH) {
        values[y * DIFF_WIDTH + x] = value(x, y).coerceIn(0, 255).toByte()
    }
    return Luma(DIFF_WIDTH, DIFF_HEIGHT, values)
}

/** A 6 × 8 block of [shape] with its left edge at [left], on [background]. */
internal fun block(left: Int, shape: Int = SHAPE, background: Int = BACKGROUND, brighter: Int = 0) = frame { x, y ->
    brighter + if (x in left until left + 6 && y in 8 until 16) shape else background
}

class FrameDiffTest {
    /** Fails if the first frame is compared against an empty or zeroed last frame. */
    @Test
    fun theFirstFrameIsNeverMovement() {
        assertThat(FrameDiff().moved(block(4))).isFalse()
    }

    /** Fails if unchanged pixels are counted. */
    @Test
    fun identicalFramesAreStill() {
        val diff = FrameDiff()
        diff.moved(block(4))
        assertThat(diff.moved(block(4))).isFalse()
    }

    /** Fails if a moved shape isn't counted, or the last frame isn't kept between calls. */
    @Test
    fun aShapeMovingIsMovement() {
        val diff = FrameDiff()
        diff.moved(block(4))
        assertThat(diff.moved(block(10))).isTrue()
    }

    /** Fails if the mean shift isn't taken off each pixel, or bytes are read signed (the background crosses 127). */
    @Test
    fun aWholeFrameGettingBrighterIsNotMovement() {
        val still = FrameDiff()
        still.moved(block(4))
        assertThat(still.moved(block(4, brighter = 80))).isFalse()

        val moving = FrameDiff()
        moving.moved(block(4))
        assertThat(moving.moved(block(10, brighter = 80))).isTrue()
    }

    /** Fails if PIXEL_DELTA falls within the noise's reach (up to 20 between two frames). */
    @Test
    fun sensorNoiseIsNotMovement() {
        val random = Random(8)
        val diff = FrameDiff()
        diff.moved(frame { _, _ -> BACKGROUND + random.nextInt(-10, 11) })
        repeat(20) {
            assertThat(diff.moved(frame { _, _ -> BACKGROUND + random.nextInt(-10, 11) })).isFalse()
        }
    }

    /** Fails if CHANGED_SHARE is ignored or applied to the wrong count (31 of 768 pixels is the first above 4 %). */
    @Test
    fun aSmallChangeBelowTheShareIsNotMovement() {
        fun changed(count: Int) = frame { x, y -> if (y * DIFF_WIDTH + x < count) BACKGROUND + 100 else BACKGROUND }

        val few = FrameDiff()
        few.moved(changed(0))
        assertThat(few.moved(changed(25))).isFalse()

        val enough = FrameDiff()
        enough.moved(changed(0))
        assertThat(enough.moved(changed(31))).isTrue()
    }

    /** Fails if bytes are read signed: 140 reads as −116, so a still room brightening across 127 looks like movement. */
    @Test
    fun valuesAboveOneTwentySevenAreRead() {
        val moving = FrameDiff()
        moving.moved(block(4, shape = 200))
        assertThat(moving.moved(block(10, shape = 200))).isTrue()

        val still = FrameDiff()
        still.moved(block(4, shape = 200, background = 100))
        assertThat(still.moved(block(4, shape = 200, background = 100, brighter = 40))).isFalse()
    }

    /** Fails if reset leaves the last frame, so the next frame is compared against it. */
    @Test
    fun resetForgetsTheLastFrame() {
        val diff = FrameDiff()
        diff.moved(block(4))
        diff.reset()
        assertThat(diff.moved(block(10))).isFalse()
        assertThat(diff.moved(block(16))).isTrue()
    }
}
