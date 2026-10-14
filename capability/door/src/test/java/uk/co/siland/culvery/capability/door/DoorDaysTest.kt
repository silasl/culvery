package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** 5 §5.2: Today and Yesterday by the household's dates, newest first. */
class DoorDaysTest {
    private val london = ZoneId.of("Europe/London")
    private var ids = 0L

    private fun at(local: String) =
        DoorEvent(++ids, LocalDateTime.parse(local).atZone(london).toInstant().toEpochMilli(), DoorKind.RING, "", false, null, false)

    /** At midnight today's rows are yesterday's, and the day before's (still kept) leave the tab. */
    @Test
    fun atMidnightTodaysRowsBecomeYesterdaysAndOlderOnesLeave() {
        val late = at("2026-10-14T23:59")
        val morning = at("2026-10-14T07:00")
        val dayBefore = at("2026-10-13T23:00")
        val older = at("2026-10-12T23:30")
        val events = listOf(late, morning, dayBefore, older)
        assertThat(doorDays(events, LocalDate.of(2026, 10, 14), london)).isEqualTo(DoorDays(listOf(late, morning), listOf(dayBefore)))
        assertThat(doorDays(events, LocalDate.of(2026, 10, 15), london)).isEqualTo(DoorDays(emptyList(), listOf(late, morning)))
    }

    /** 00:30 BST on the 15th is 23:30 UTC on the 14th: the household's date wins. */
    @Test
    fun daysAreTheHouseholdsNotUtc() {
        val justAfterMidnight = at("2026-10-15T00:30")
        assertThat(doorDays(listOf(justAfterMidnight), LocalDate.of(2026, 10, 15), london).today).containsExactly(justAfterMidnight)
    }
}
