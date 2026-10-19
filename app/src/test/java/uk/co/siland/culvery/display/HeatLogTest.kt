package uk.co.siland.culvery.display

import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

private class FakeTabletHeat(var heat: HeatReading) : TabletHeat {
    private var listener: () -> Unit = {}

    override fun reading() = heat

    override fun onStatusChange(listener: () -> Unit): AutoCloseable {
        this.listener = listener
        return AutoCloseable { this.listener = {} }
    }

    fun changeTo(next: HeatReading) {
        heat = next
        listener()
    }
}

private class Rig(private val test: TestScope, first: HeatReading = HeatReading(0, 305, 80)) {
    val tablet = FakeTabletHeat(first)

    init {
        ShadowLog.clear()
        HeatLog(tablet, test.backgroundScope).start()
        test.runCurrent()
    }

    fun after(ms: Long) {
        test.advanceTimeBy(ms)
        test.runCurrent()
    }

    fun entries() = ShadowLog.getLogsForTag("HeatLog")

    fun lines() = entries().map { it.msg }
}

@RunWith(AndroidJUnit4::class)
class HeatLogTest {
    /** Fails if starting logs nothing, or the line lacks the status or the battery. */
    @Test
    fun oneLineAtStart() = runTest {
        val rig = Rig(this)
        assertThat(rig.lines()).containsExactly("Heat: thermal 0, battery 305 tenths of a degree, 80 percent")
    }

    /** Fails if a status change logs no line, or one that leaves out the battery as it is then. */
    @Test
    fun aStatusChangeLogsOneLineWithTheBattery() = runTest {
        val rig = Rig(this)
        rig.tablet.changeTo(HeatReading(3, 412, 71))
        assertThat(rig.lines()).hasSize(2)
        assertThat(rig.lines().last()).isEqualTo("Heat: thermal 3, battery 412 tenths of a degree, 71 percent")
    }

    /** Fails if the platform repeating the current status (as it does on registration) adds a line. */
    @Test
    fun aRepeatedStatusIsNotLoggedAgain() = runTest {
        val rig = Rig(this)
        rig.tablet.changeTo(HeatReading(0, 306, 80))
        assertThat(rig.lines()).hasSize(1)
        rig.tablet.changeTo(HeatReading(2, 330, 79))
        rig.tablet.changeTo(HeatReading(2, 331, 79))
        assertThat(rig.lines()).hasSize(2)
    }

    /** Fails if the battery line comes a millisecond early, late, or stops after the first. */
    @Test
    fun theBatteryIsLoggedEveryFifteenMinutes() = runTest {
        val rig = Rig(this)
        rig.after(899_999)
        assertThat(rig.lines()).hasSize(1)
        rig.after(1)
        assertThat(rig.lines()).hasSize(2)
        rig.after(900_000)
        assertThat(rig.lines()).hasSize(3)
    }

    /** Fails if a line is below warning level (release drops it), has another tag, or carries anything but numbers. */
    @Test
    fun theLinesAreWarningsWithNumbersOnly() = runTest {
        val rig = Rig(this, HeatReading(1, null, null))
        rig.tablet.changeTo(HeatReading(2, 312, 45))
        rig.after(900_000)
        assertThat(rig.entries()).hasSize(3)
        val shape = Regex("""^Heat: thermal \d+, battery (\d+|null) tenths of a degree, (\d+|null) percent$""")
        rig.entries().forEach {
            assertThat(it.type).isEqualTo(Log.WARN)
            assertThat(it.msg).matches(shape.toPattern())
        }
        assertThat(rig.lines().first()).isEqualTo("Heat: thermal 1, battery null tenths of a degree, null percent")
    }

    /** Fails if the temperature, the percentage (level over scale) or a missing intent is read wrongly. */
    @Test
    fun readingOfReadsTheBatteryIntent() {
        val battery = Intent()
            .putExtra(BatteryManager.EXTRA_TEMPERATURE, 312)
            .putExtra(BatteryManager.EXTRA_LEVEL, 45)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
        assertThat(readingOf(2, battery)).isEqualTo(HeatReading(2, 312, 45))
        assertThat(readingOf(2, null)).isEqualTo(HeatReading(2, null, null))
        assertThat(readingOf(2, Intent().putExtra(BatteryManager.EXTRA_LEVEL, 1).putExtra(BatteryManager.EXTRA_SCALE, 4)))
            .isEqualTo(HeatReading(2, null, 25))
    }
}
