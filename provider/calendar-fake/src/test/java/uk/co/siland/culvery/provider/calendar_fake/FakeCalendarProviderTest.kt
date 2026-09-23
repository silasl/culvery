package uk.co.siland.culvery.provider.calendar_fake

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.core.plugin.Connection

class FakeCalendarProviderTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val conn = Connection("c1", FakeCalendarProvider.ID, "Sample calendar", emptyMap())
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    private fun providerOn(date: LocalDate) =
        FakeCalendarProvider(Clock.fixed(date.atTime(10, 0).atZone(zone).toInstant(), zone))

    private fun windowFrom(date: LocalDate) = DateRange(date.minusDays(1), date.plusDays(15), zone)

    private fun time(t: EventTime) = t.instantIn(zone).atZone(zone)

    private fun source(id: String) = FakeCalendarProvider.SOURCES.first { it.id == id }

    @Test
    fun sourcesAreThePeopleFamilyAndSchoolTermsAllReadOnly() = runTest {
        val sources = providerOn(today).sources(conn)
        assertThat(sources.map { it.name }).containsExactly("Alex", "Sam", "Mia", "Family", "School terms").inOrder()
        assertThat(sources.none { it.writable }).isTrue()
    }

    @Test
    fun todayMatchesTheHandOff() = runTest {
        val fake = providerOn(today)
        val todays = fake.sources(conn)
            .flatMap { s -> fake.sync(conn, s, windowFrom(today), null).upserts.map { s.id to it } }
            .filter { (_, e) -> e.start is EventTime.Timed && time(e.start).toLocalDate() == today }
            .sortedBy { (_, e) -> e.start.instantIn(zone) }
            .map { (source, e) -> "${time(e.start).format(hm)}–${time(e.end).format(hm)} ${e.title} ($source)" }
        assertThat(todays).containsExactly(
            "07:45–08:30 School run (fake-sam)",
            "10:00–11:00 Boiler service (fake-family)",
            "13:00–13:30 Plumber quote call (fake-family)",
            "16:00–17:00 Swimming (fake-mia)",
            "19:30–21:00 Dinner with Jo & Priya (fake-alex)",
        ).inOrder()
    }

    @Test
    fun pianoRepeatsWeeklyOnMiasCalendar() = runTest {
        val fake = providerOn(today)
        val piano = fake.sync(conn, source(FakeCalendarProvider.SOURCE_MIA), windowFrom(today), null).upserts
            .filter { it.title == "Piano" }
        assertThat(piano.map { time(it.start).toLocalDateTime().toString() })
            .containsExactly("2026-09-26T15:30", "2026-10-03T15:30").inOrder()
        assertThat(piano.all { it.recurring }).isTrue()
    }

    @Test
    fun swimmingAndBinDayRepeatWeekly() = runTest {
        val fake = providerOn(today)
        val swimming = fake.sync(conn, source(FakeCalendarProvider.SOURCE_MIA), windowFrom(today), null).upserts
            .filter { it.title == "Swimming" }
        assertThat(swimming.map { time(it.start).toLocalDate().toString() })
            .containsExactly("2026-09-23", "2026-09-30", "2026-10-07").inOrder()
        assertThat(swimming.all { it.recurring }).isTrue()

        val bins = fake.sync(conn, source(FakeCalendarProvider.SOURCE_FAMILY), windowFrom(today), null).upserts
            .filter { it.title == "Bin day" }
        assertThat(bins.map { it.start }).containsExactly(
            EventTime.AllDay(LocalDate.of(2026, 9, 25)),
            EventTime.AllDay(LocalDate.of(2026, 10, 2)),
        ).inOrder()
        assertThat(bins.map { it.end }).containsExactly(
            EventTime.AllDay(LocalDate.of(2026, 9, 26)),
            EventTime.AllDay(LocalDate.of(2026, 10, 3)),
        ).inOrder()
        assertThat(bins.all { it.recurring }).isTrue()
    }

    @Test
    fun insetDayComesFromTheReadOnlySchoolTermsCalendar() = runTest {
        val school = source(FakeCalendarProvider.SOURCE_SCHOOL)
        val events = providerOn(today).sync(conn, school, windowFrom(today), null).upserts
        assertThat(school.name).isEqualTo("School terms")
        assertThat(school.writable).isFalse()
        assertThat(events.map { Triple(it.title, it.start, it.end) }).containsExactly(
            Triple("INSET day — no school", EventTime.AllDay(LocalDate.of(2026, 9, 26)), EventTime.AllDay(LocalDate.of(2026, 9, 27))),
        )
    }

    @Test
    fun halfTermIsAThreeDayAllDayEventAfterTheVisibleWeek() = runTest {
        val fake = providerOn(today)
        val halfTerm = fake.sync(conn, source(FakeCalendarProvider.SOURCE_FAMILY), windowFrom(today), null).upserts
            .single { it.title == "Half term" }
        assertThat(halfTerm.start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 10, 1)))
        assertThat(halfTerm.end).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun cursorFromYesterdayGivesAFullReplace() = runTest {
        val source = FakeCalendarProvider.SOURCES.first()
        val yesterday = providerOn(today).sync(conn, source, windowFrom(today), null)
        val tomorrow = today.plusDays(1)
        val next = providerOn(tomorrow).sync(conn, source, windowFrom(tomorrow), yesterday.cursor)
        assertThat(next.fullReplace).isTrue()
        assertThat(next.upserts).isNotEmpty()
        assertThat(next.cursor).isNotEqualTo(yesterday.cursor)
    }

    @Test
    fun failNextWithFailsOnlyTheNextCall() = runTest {
        val fake = providerOn(today)
        fake.failNextWith(UnreachableException("offline"))
        val first = runCatching { fake.sources(conn) }
        assertThat(first.exceptionOrNull()).isInstanceOf(UnreachableException::class.java)
        assertThat(fake.sources(conn)).hasSize(5)
    }
}
