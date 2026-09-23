package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

class ContractTypesTest {
    private val london = ZoneId.of("Europe/London")
    private val range = DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 30), london)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        EventTime.Timed(LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(london).toInstant())

    private fun allDay(month: Int, day: Int) = EventTime.AllDay(LocalDate.of(2026, month, day))

    @Test
    fun rangeMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 23), london)
        }
    }

    @Test
    fun allDayStartsAtMidnightInTheGivenZone() {
        assertThat(allDay(10, 24).instantIn(london)).isEqualTo(Instant.parse("2026-10-23T23:00:00Z"))
    }

    @Test
    fun timedEventIsTheSameInstantInAnyZone() {
        val t = at(24, 9)
        assertThat(t.instantIn(ZoneId.of("Asia/Tokyo"))).isEqualTo(t.instant)
    }

    @Test
    fun timedEventInsideTheRangeOverlaps() {
        assertThat(range.overlaps(at(24, 9), at(24, 10))).isTrue()
    }

    @Test
    fun eventEndingAtTheRangeStartDoesNotOverlap() {
        assertThat(range.overlaps(at(22, 23), at(23, 0))).isFalse()
    }

    @Test
    fun allDayEventOnTheExclusiveEndDayDoesNotOverlap() {
        assertThat(range.overlaps(allDay(9, 30), allDay(10, 1))).isFalse()
        assertThat(range.overlaps(allDay(9, 29), allDay(9, 30))).isTrue()
    }

    @Test
    fun multiDayAllDayEventStartingBeforeTheRangeOverlaps() {
        assertThat(range.overlaps(allDay(9, 20), allDay(9, 25))).isTrue()
    }

    @Test
    fun zeroLengthEventAtTheRangeStartOverlaps() {
        assertThat(range.overlaps(at(23, 0), at(23, 0))).isTrue()
    }
}
