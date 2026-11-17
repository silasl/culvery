package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.di.DebugClock
import uk.co.siland.culvery.di.parseClockTime

class DebugClockTest {
    private val zone = ZoneId.of("Europe/London")
    private fun clockAt(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli().let { now -> DebugClock { now } }
    private fun DebugClock.reads(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), zone)

    /** 11 §8.1: Fails if 90 minutes doesn't put the clock 5 400 000 ms ahead, or 0 doesn't clear it. */
    @Test
    fun theOffsetMovesTheClockAndZeroClearsIt() {
        val clock = DebugClock { 1_000_000L }
        assertThat(clock.nowMillis()).isEqualTo(1_000_000L)
        clock.minutes = 90
        assertThat(clock.nowMillis()).isEqualTo(1_000_000L + 5_400_000L)
        clock.minutes = 0
        assertThat(clock.nowMillis()).isEqualTo(1_000_000L)
    }

    @Test
    fun atMovesForwardToTheNextSuchTime() {
        val clock = clockAt("2026-11-18T10:00:30")
        clock.moveTo(LocalTime.of(18, 0), zone)
        assertThat(clock.reads()).isEqualTo(LocalDateTime.parse("2026-11-18T18:00:30"))
    }

    @Test
    fun aTimeAlreadyPastTodayIsTomorrows() {
        val clock = clockAt("2026-11-18T19:00:00")
        clock.moveTo(LocalTime.of(18, 0), zone)
        assertThat(clock.reads()).isEqualTo(LocalDateTime.parse("2026-11-19T18:00:00"))
        clock.moveTo(LocalTime.of(19, 0), zone)
        assertThat(clock.reads()).isEqualTo(LocalDateTime.parse("2026-11-19T19:00:00"))
    }

    @Test
    fun atNeverMovesTheWallBackwards() {
        val clock = clockAt("2026-11-18T19:00:00")
        clock.moveTo(LocalTime.of(18, 0), zone)
        clock.moveTo(LocalTime.of(7, 0), zone)
        assertThat(clock.reads()).isEqualTo(LocalDateTime.parse("2026-11-20T07:00:00"))
    }

    @Test
    fun addStacksOnTheOffset() {
        val clock = DebugClock { 1_000_000L }
        clock.minutes = 30
        clock.add(15)
        assertThat(clock.minutes).isEqualTo(45)
        clock.add(-45)
        assertThat(clock.minutes).isEqualTo(0)
    }

    @Test
    fun aMistypedTimeMovesNothing() {
        assertThat(parseClockTime("18:30")).isEqualTo(LocalTime.of(18, 30))
        listOf("", "6pm", "25:00", "18:60", "18.30", "9:30").forEach { assertThat(parseClockTime(it)).isNull() }
    }
}
