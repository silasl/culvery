package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** 5 §5.2, 15a §3.1: every kept day by the household's dates, newest first. */
class DoorDaysTest {
    private val london = ZoneId.of("Europe/London")
    private var ids = 0L

    private fun at(local: String) =
        DoorEvent(++ids, LocalDateTime.parse(local).atZone(london).toInstant().toEpochMilli(), DoorKind.RING, "", false, null, false)

    private fun atUtc(utc: String) = DoorEvent(++ids, Instant.parse(utc).toEpochMilli(), DoorKind.RING, "", false, null, false)

    @Test
    fun groupsEveryKeptDayNewestFirst() {
        val a = at("2026-10-30T09:00")
        val b = at("2026-10-29T22:00")
        val c = at("2026-10-27T08:00")
        val days = doorDays(listOf(a, b, c), LocalDate.of(2026, 10, 30), london, KeepDays.SEVEN)
        assertThat(days).containsExactly(
            DoorDay(LocalDate.of(2026, 10, 30), listOf(a)),
            DoorDay(LocalDate.of(2026, 10, 29), listOf(b)),
            DoorDay(LocalDate.of(2026, 10, 27), listOf(c)),
        ).inOrder()
    }

    /** The prune may not have run yet: a day past the cut isn't shown, and one just inside it is. */
    @Test
    fun dropsADayPastTheCut() {
        val past = at("2026-10-28T12:00")
        val yesterday = at("2026-10-29T00:00")
        val days = doorDays(listOf(yesterday, past), LocalDate.of(2026, 10, 30), london, KeepDays.TWO)
        assertThat(days.map { it.date }).containsExactly(LocalDate.of(2026, 10, 29))
    }

    @Test
    fun eventsOfADayStayNewestFirst() {
        val late = at("2026-10-30T10:00")
        val early = at("2026-10-30T08:00")
        assertThat(doorDays(listOf(late, early), LocalDate.of(2026, 10, 30), london, KeepDays.TWO).single().events).containsExactly(late, early).inOrder()
    }

    /** 25 October 2026 has 25 hours: 23:30Z on the 24th (00:30 BST) and 23:30Z on the 25th (23:30 GMT) are both on the 25th. */
    @Test
    fun theLongDayIsOneDay() {
        val first = atUtc("2026-10-24T23:30:00Z")
        val last = atUtc("2026-10-25T23:30:00Z")
        val days = doorDays(listOf(last, first), LocalDate.of(2026, 10, 26), london, KeepDays.TWO)
        assertThat(days).containsExactly(DoorDay(LocalDate.of(2026, 10, 25), listOf(last, first)))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 10, 30)
        assertThat(dayLabel(today, today)).isEqualTo("Today")
        assertThat(dayLabel(LocalDate.of(2026, 10, 29), today)).isEqualTo("Yesterday")
        assertThat(dayLabel(LocalDate.of(2026, 10, 27), today)).isEqualTo("Tue 27 Oct")
    }
}
