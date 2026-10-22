package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.db.EventEntity
import uk.co.siland.culvery.capability.calendar.db.EventRow
import uk.co.siland.culvery.capability.calendar.db.RememberedTitleEntity

class RememberedTitlesTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)

    private fun day(month: Int, d: Int, year: Int = 2026) = LocalDate.of(year, month, d)

    private fun instant(date: LocalDate, hour: Int, minute: Int = 0): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun timed(
        id: String,
        title: String,
        date: LocalDate,
        hour: Int = 9,
        minutes: Int = 60,
        rule: String? = null,
        seriesId: String? = null,
        forPerson: String? = null,
        sourcePerson: String = "family",
        sourceId: String = "s1",
    ): EventRow {
        val start = instant(date, hour)
        return EventRow(
            EventEntity(
                "c1", sourceId, id, title, start, null, start + minutes * 60_000L, null, rule != null || seriesId != null,
                forPerson, null, start, start + minutes * 60_000L, rule, seriesId,
            ),
            sourcePerson,
        )
    }

    private fun allDay(id: String, title: String, from: LocalDate, to: LocalDate, sourcePerson: String = "family") = EventRow(
        EventEntity(
            "c1", "s1", id, title, null, from.toString(), null, to.toString(), false, null, null,
            from.atStartOfDay(zone).toInstant().toEpochMilli(), to.atStartOfDay(zone).toInstant().toEpochMilli(), null, null,
        ),
        sourcePerson,
    )

    private fun remembered(vararg rows: EventRow): List<RememberedTitleEntity> = rememberedFrom(rows.toList(), zone, today)

    @Test
    fun theKeyTrimsCollapsesLowersAndDropsAccents() {
        assertThat(titleKey("  Café  Lunch ")).isEqualTo("cafe lunch")
        assertThat(titleKey("Cafe")).isEqualTo(titleKey("CAFÉ"))
        assertThat(titleKey("   ")).isEmpty()
    }

    @Test
    fun theRememberedDateIsTheLatestPastElseTheEarliestFuture() {
        val past1 = day(9, 10)
        val past2 = day(9, 20)
        val soon = day(9, 25)
        val later = day(10, 2)
        assertThat(better(past1, past2, today)).isEqualTo(past2)
        assertThat(better(past2, past1, today)).isEqualTo(past2)
        assertThat(better(past1, soon, today)).isEqualTo(past1)
        assertThat(better(soon, past1, today)).isEqualTo(past1)
        assertThat(better(soon, later, today)).isEqualTo(soon)
        assertThat(better(later, soon, today)).isEqualTo(soon)
        assertThat(better(today, soon, today)).isEqualTo(today)
    }

    @Test
    fun aSeriesCountsOnce() {
        val rule = "DTSTART:20260905\nRRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=8"
        val rows = (0..3).map { timed("swim_$it", "Swim", day(9, 5).plusWeeks(it.toLong()), rule = rule, seriesId = "swim") }
        val one = remembered(*rows.toTypedArray()).single()
        assertThat(one.sourceEventId).isEqualTo("swim")
        assertThat(one.rememberedDate).isEqualTo("2026-09-19")
        assertThat(one.seriesStart).isEqualTo("2026-09-05")
    }

    @Test
    fun aPhonesLastThursdayKeepsItsLast() {
        val rule = "DTSTART:20260924\nRRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH"
        val kept = remembered(timed("a", "Club", day(9, 24), rule = rule, seriesId = "a")).single()
        assertThat(kept.rule).isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH")
    }

    @Test
    fun aRuleTheEditorDoesNotOfferIsNotKept() {
        val every3 = timed("a", "Club", day(9, 5), rule = "RRULE:FREQ=WEEKLY;INTERVAL=3", seriesId = "a")
        val unreadable = timed("b", "Choir", day(9, 5), rule = "RRULE:FREQ", seriesId = "b")
        val byDate = timed("c", "Bins", day(9, 5), rule = "RRULE:FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=5", seriesId = "c")
        val result = remembered(every3, unreadable, byDate).associateBy { it.titleKey }
        assertThat(result.keys).containsExactly("club", "choir", "bins")
        assertThat(result.values.map { it.rule }).containsExactly(null, null, null)
    }

    @Test
    fun aKeptRuleHasNoCountUntilOrDtstart() {
        val weekly = timed("a", "Swim", day(9, 5), rule = "DTSTART:20260905\nRRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=8;WKST=MO", seriesId = "a")
        val fourth = timed("b", "Choir", day(9, 26), rule = "RRULE:FREQ=MONTHLY;BYDAY=4SA;UNTIL=20271231", seriesId = "b")
        val result = remembered(weekly, fourth).associateBy { it.titleKey }
        assertThat(result.getValue("swim").rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
        assertThat(result.getValue("choir").rule).isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA")
        assertThat(result.getValue("choir").seriesStart).isNull()
    }

    @Test
    fun anAllDayEventKeepsItsDays() {
        val trip = remembered(allDay("t", "Camping", day(9, 18), day(9, 21))).single()
        assertThat(listOf(trip.allDay, trip.days, trip.startMinute, trip.lengthMinutes)).containsExactly(true, 3, null, null).inOrder()
        assertThat(trip.rememberedDate).isEqualTo("2026-09-18")
    }

    @Test
    fun aTimedEventKeepsItsStartAndLength() {
        val lesson = remembered(timed("g", "Guitar lesson", day(9, 21), hour = 16, minutes = 45)).single()
        assertThat(listOf(lesson.allDay, lesson.startMinute, lesson.lengthMinutes, lesson.days)).containsExactly(false, 16 * 60, 45, null).inOrder()
        assertThat(lesson.title).isEqualTo("Guitar lesson")
    }

    @Test
    fun anUntaggedEventKeepsItsCalendarsPerson() {
        val untagged = timed("a", "Swim", day(9, 21), sourcePerson = "mia")
        val tagged = timed("b", "Dentist", day(9, 21), forPerson = "alex", sourcePerson = "mia")
        val result = remembered(untagged, tagged).associateBy { it.titleKey }
        assertThat(result.getValue("swim").forPerson).isEqualTo("mia")
        assertThat(result.getValue("dentist").forPerson).isEqualTo("alex")
    }

    @Test
    fun aBlankTitleIsNotRemembered() {
        assertThat(remembered(timed("a", "   ", day(9, 21)), timed("b", "", day(9, 22)))).isEmpty()
    }

    @Test
    fun theLatestDateWinsAmongEventsWithOneTitle() {
        val old = timed("a", "swim", day(9, 1), hour = 8)
        val recent = timed("b", "Swim", day(9, 21), hour = 10)
        val future = timed("c", "SWIM", day(10, 1), hour = 11)
        val one = remembered(old, future, recent).single()
        assertThat(one.sourceEventId).isEqualTo("b")
        assertThat(one.title).isEqualTo("Swim")
    }

    @Test
    fun aTitleOlderThanTwelveMonthsIsLeftOut() {
        assertThat(remembered(timed("a", "Ancient", day(9, 22, year = 2025)))).isEmpty()
        assertThat(remembered(timed("b", "Edge", day(9, 23, year = 2025))).single().titleKey).isEqualTo("edge")
    }

    @Test
    fun anEventJustAfterLocalMidnightIsRememberedOnItsLocalDay() {
        val late = timed("a", "Late", day(9, 22), hour = 0, minutes = 30)
        assertThat(remembered(late).single().rememberedDate).isEqualTo("2026-09-22")
    }
}
