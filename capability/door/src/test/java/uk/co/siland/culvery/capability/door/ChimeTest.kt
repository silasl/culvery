package uk.co.siland.culvery.capability.door

import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChimeTest {
    private class Wav(val channels: Int, val rate: Int, val bits: Int, val format: Int, val samples: ShortArray) {
        val seconds get() = samples.size / rate.toDouble()
        fun peak() = samples.maxOf { abs(it.toInt()) }
        fun rms(fromSecond: Double, toSecond: Double): Double {
            val slice = samples.copyOfRange((fromSecond * rate).toInt(), (toSecond * rate).toInt())
            return sqrt(slice.sumOf { it.toDouble() * it } / slice.size)
        }
    }

    private fun wav(sound: DoorSound): Wav {
        val bytes = ApplicationProvider.getApplicationContext<android.content.Context>().resources.openRawResource(sound.res).use { it.readBytes() }
        assertThat(String(bytes, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(bytes, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = buf.getInt(at + 4)
            if (id == "fmt ") {
                format = buf.getShort(at + 8).toInt()
                channels = buf.getShort(at + 10).toInt()
                rate = buf.getInt(at + 12)
                bits = buf.getShort(at + 22).toInt()
            } else if (id == "data") {
                val samples = ShortArray(size / 2) { buf.getShort(at + 8 + it * 2) }
                return Wav(channels, rate, bits, format, samples)
            }
            at += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    @Test
    fun theChimeIsANotificationEvent() = assertThat(CHIME_ATTRIBUTES.usage).isEqualTo(AudioAttributes.USAGE_NOTIFICATION_EVENT)

    @Test
    fun bothSoundsAreBundledWavs() {
        for (sound in DoorSound.entries) {
            val w = wav(sound)
            assertThat(listOf(w.format, w.channels, w.rate, w.bits)).containsExactly(1, 1, 22_050, 16).inOrder()
        }
        assertThat(wav(DoorSound.ORGAN).seconds).isIn(com.google.common.collect.Range.closed(3.4, 4.1))
        assertThat(wav(DoorSound.CLASSIC).seconds).isWithin(0.01).of(3.2)
    }

    @Test
    fun theOrganIsAboutAsLoudAsTheChime() {
        val organ = wav(DoorSound.ORGAN)
        val ratio = organ.rms(0.0, 3.5) / wav(DoorSound.CLASSIC).rms(0.0, 3.0)
        assertThat(ratio).isAtLeast(1.0)
        assertThat(ratio).isAtMost(1.5)
        assertThat(organ.peak()).isAtMost((0.95 * 32_767).toInt() + 1)
    }

    @Test
    fun theOrganDoesNotSwellAsItFades() {
        val organ = wav(DoorSound.ORGAN)
        fun peak(from: Double, to: Double) = organ.samples.copyOfRange((from * organ.rate).toInt(), (to * organ.rate).toInt()).maxOf { abs(it.toInt()) }
        val before = peak(3.0, 3.5)
        val window = organ.rate / 100
        for (start in (3.5 * organ.rate).toInt() until organ.samples.size - window step window) {
            val windowPeak = organ.samples.copyOfRange(start, start + window).maxOf { abs(it.toInt()) }
            assertThat(windowPeak).isAtMost((before * 1.05).toInt())
        }
    }

    @Test
    fun theOrganPlaysOnceAndFades() {
        val organ = wav(DoorSound.ORGAN)
        val quiet = (0.01 * 32_767).toInt()
        val run = organ.rate / 2
        var silent = 0
        for (i in 0 until (3.4 * organ.rate).toInt()) {
            silent = if (abs(organ.samples[i].toInt()) < quiet) silent + 1 else 0
            assertThat(silent).isLessThan(run)
        }
        val tail = organ.samples.takeLast(organ.rate / 20)
        assertThat(tail.maxOf { abs(it.toInt()) }).isLessThan((0.02 * 32_767).toInt())
    }
}
