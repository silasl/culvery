package uk.co.siland.culvery.provider.calendar_fake

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.capability.calendar.newClientKey
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

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
    fun onlyTheFamilyCalendarIsWritable() = runTest {
        val sources = providerOn(today).sources(conn)
        assertThat(sources.map { it.name }).containsExactly("Alex", "Sam", "Mia", "Family calendar", "School terms").inOrder()
        assertThat(sources.filter { it.writable }.map { it.id }).containsExactly(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(providerOn(today).descriptor.features).containsExactly(Feature.READ, Feature.WRITE)
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
            "07:45–08:30 School run (fake-family)",
            "10:00–11:00 Boiler service (fake-family)",
            "13:00–13:30 Plumber quote call (fake-family)",
            "16:00–17:00 Swimming (fake-family)",
            "19:30–21:00 Dinner with Jo & Priya (fake-family)",
        ).inOrder()
    }

    @Test
    fun pianoRepeatsWeeklyOnTheFamilyCalendar() = runTest {
        val fake = providerOn(today)
        val piano = fake.sync(conn, source(FakeCalendarProvider.SOURCE_FAMILY), windowFrom(today), null).upserts
            .filter { it.title == "Piano" }
        assertThat(piano.map { time(it.start).toLocalDateTime().toString() })
            .containsExactly("2026-09-26T15:30", "2026-10-03T15:30").inOrder()
        assertThat(piano.all { it.recurring }).isTrue()
    }

    @Test
    fun swimmingAndBinDayRepeatWeekly() = runTest {
        val fake = providerOn(today)
        val swimming = fake.sync(conn, source(FakeCalendarProvider.SOURCE_FAMILY), windowFrom(today), null).upserts
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
        val source = source(FakeCalendarProvider.SOURCE_FAMILY)
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

    private val family get() = source(FakeCalendarProvider.SOURCE_FAMILY)

    private suspend fun FakeCalendarProvider.familyEvents(): List<RemoteEvent> =
        sync(conn, family, windowFrom(today), null).upserts

    private fun draft(title: String) = EventDraft(
        title,
        EventTime.Timed(today.plusDays(1).atTime(18, 0).atZone(zone).toInstant()),
        EventTime.Timed(today.plusDays(1).atTime(19, 0).atZone(zone).toInstant()),
        forPerson = "mia-id",
        createdBy = "sam-id",
    )

    @Test
    fun samplesAreUntaggedExceptFamilyUntilTheSeedGivesIds() = runTest {
        val byTitle = providerOn(today).familyEvents().associateBy { it.title }
        assertThat(byTitle.getValue("Dinner with Jo & Priya").let { it.forPerson to it.createdBy }).isEqualTo(null to null)
        assertThat(byTitle.getValue("Boiler service").forPerson).isEqualTo("family")
    }

    @Test
    fun tagSamplesTagsTheWeekWithTheHouseholdsIds() = runTest {
        val fake = providerOn(today)
        fake.tagSamples(mapOf("Alex" to "alex-id", "Sam" to "sam-id", "Mia" to "mia-id"))
        val byTitle = fake.familyEvents().associateBy { it.title }
        assertThat(byTitle.getValue("Dinner with Jo & Priya").let { it.forPerson to it.createdBy }).isEqualTo("alex-id" to "alex-id")
        assertThat(byTitle.getValue("Football").let { it.forPerson to it.createdBy }).isEqualTo("mia-id" to "mia-id")
        assertThat(byTitle.getValue("Boiler service").let { it.forPerson to it.createdBy }).isEqualTo("family" to "alex-id")
        assertThat(byTitle.getValue("Plumber quote call").let { it.forPerson to it.createdBy }).isEqualTo(null to null)
    }

    @Test
    fun tagSamplesForcesAFullReplaceOnlyWhenTheIdsChange() = runTest {
        val fake = providerOn(today)
        val first = fake.sync(conn, family, windowFrom(today), null)
        fake.tagSamples(mapOf("Alex" to "alex-id"))
        val second = fake.sync(conn, family, windowFrom(today), first.cursor)
        assertThat(second.fullReplace).isTrue()
        fake.tagSamples(mapOf("Alex" to "alex-id"))
        assertThat(fake.sync(conn, family, windowFrom(today), second.cursor).fullReplace).isFalse()
    }

    @Test
    fun deletingASampleHidesItAndDeletingItAgainSucceeds() = runTest {
        val fake = providerOn(today)
        val boiler = fake.familyEvents().single { it.title == "Boiler service" }
        fake.delete(conn, family, boiler.remoteId)
        fake.delete(conn, family, boiler.remoteId)
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Boiler service")
    }

    @Test
    fun updatingASampleReplacesItOnTheNextSync() = runTest {
        val fake = providerOn(today)
        val plumber = fake.familyEvents().single { it.title == "Plumber quote call" }
        fake.update(conn, family, plumber.remoteId, EventDraft(plumber.title, plumber.start, plumber.end, "sam-id", null), setOf(EventField.FOR_PERSON))
        assertThat(fake.familyEvents().single { it.remoteId == plumber.remoteId }.forPerson).isEqualTo("sam-id")
    }

    @Test
    fun repeatingSamplesCannotBeChanged() = runTest {
        val fake = providerOn(today)
        val swim = fake.familyEvents().first { it.title == "Swimming" }
        assertThrows(WriteRejectedException::class.java) {
            runBlocking { fake.update(conn, family, swim.remoteId, EventDraft(swim.title, swim.start, swim.end, null, null), setOf(EventField.TITLE)) }
        }
    }

    @Test
    fun readOnlySourcesCannotBeWritten() = runTest {
        val fake = providerOn(today)
        assertThrows(WriteRejectedException::class.java) {
            runBlocking { fake.create(conn, source(FakeCalendarProvider.SOURCE_SCHOOL), draft("Sports day"), newClientKey()) }
        }
    }

    @Test
    fun rejectNextWriteRejectsOnlyTheNextWrite() = runTest {
        val fake = providerOn(today)
        fake.rejectNextWrite("Event is locked")
        val error = runCatching { fake.create(conn, family, draft("Sleepover"), newClientKey()) }.exceptionOrNull()
        assertThat(error).isInstanceOf(WriteRejectedException::class.java)
        assertThat(error?.message).isEqualTo("Event is locked")
        assertThat(fake.create(conn, family, draft("Sleepover"), newClientKey()).title).isEqualTo("Sleepover")
    }

    @Test
    fun unreachableNextWriteFailsOnlyTheNextWrite() = runTest {
        val fake = providerOn(today)
        fake.unreachableNextWrite()
        assertThat(runCatching { fake.create(conn, family, draft("Sleepover"), newClientKey()) }.exceptionOrNull())
            .isInstanceOf(UnreachableException::class.java)
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Sleepover")
        fake.create(conn, family, draft("Sleepover"), newClientKey())
        assertThat(fake.familyEvents().map { it.title }).contains("Sleepover")
    }

    @Test
    fun aCreateUsesItsClientKeyAsTheEventsId() = runTest {
        val fake = providerOn(today)
        val key = newClientKey()
        assertThat(fake.create(conn, family, draft("Sleepover"), key).remoteId).isEqualTo(key)
        assertThat(fake.familyEvents().single { it.title == "Sleepover" }.remoteId).isEqualTo(key)
        // A repeated key returns the event it made.
        assertThat(fake.create(conn, family, draft("Sleepover"), key).remoteId).isEqualTo(key)
        assertThat(fake.familyEvents().count { it.title == "Sleepover" }).isEqualTo(1)
    }

    @Test
    fun offlineFailsEveryReadAndWriteUntilItIsBackOnline() = runTest {
        val fake = providerOn(today)
        fake.setOffline(true)
        assertThat(runCatching { fake.sources(conn) }.exceptionOrNull()).isInstanceOf(UnreachableException::class.java)
        assertThat(runCatching { fake.familyEvents() }.exceptionOrNull()).isInstanceOf(UnreachableException::class.java)
        // Every write, not only the next one.
        repeat(2) {
            assertThat(runCatching { fake.create(conn, family, draft("Sleepover"), newClientKey()) }.exceptionOrNull())
                .isInstanceOf(UnreachableException::class.java)
        }
        fake.setOffline(false)
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Sleepover")
        fake.create(conn, family, draft("Sleepover"), newClientKey())
        assertThat(fake.familyEvents().map { it.title }).contains("Sleepover")
    }

    @Test
    fun theSampleIsNeverOfferedToConnectAndItsFamilyCalendarIsThePrimary() = runTest {
        val fake = providerOn(today)
        assertThat(fake.descriptor.userConnectable).isFalse()
        assertThat(fake.sources(conn).filter { it.primary }.map { it.id }).containsExactly(FakeCalendarProvider.SOURCE_FAMILY)
    }

    @Test
    fun anUpdateOfASampleChangesOnlyItsFieldsAndKeepsItsCreator() = runTest {
        val fake = providerOn(today)
        fake.tagSamples(mapOf("Alex" to "alex-id"))
        val dinner = fake.familyEvents().single { it.title == "Dinner with Jo & Priya" }
        val updated = fake.update(conn, family, dinner.remoteId, draft("Dinner at Gran's"), setOf(EventField.TITLE))
        assertThat(listOf(updated.title, updated.start, updated.forPerson, updated.createdBy))
            .containsExactly("Dinner at Gran's", dinner.start, "alex-id", "alex-id").inOrder()
        assertThat(fake.familyEvents().single { it.remoteId == dinner.remoteId }.title).isEqualTo("Dinner at Gran's")
    }

    @Test
    fun findReturnsWhatTheCalendarHoldsAndNullOnceDeleted() = runTest {
        val fake = providerOn(today)
        val boiler = fake.familyEvents().single { it.title == "Boiler service" }
        assertThat(fake.find(conn, family, boiler.remoteId)?.title).isEqualTo("Boiler service")
        fake.delete(conn, family, boiler.remoteId)
        assertThat(fake.find(conn, family, boiler.remoteId)).isNull()
    }

    @Test
    fun weeklySamplesCarryTheirRule() = runTest {
        val events = providerOn(today).familyEvents()
        assertThat(events.first { it.title == "Piano" }.recurrenceRule).isEqualTo("RRULE:FREQ=WEEKLY")
        assertThat(events.first { it.title == "Boiler service" }.recurrenceRule).isNull()
    }

    @Test
    fun aKeyWhoseEventWasDeletedIsRefused() = runTest {
        val fake = providerOn(today)
        val key = newClientKey()
        fake.create(conn, family, draft("Sleepover"), key)
        fake.delete(conn, family, key)
        assertThrows(WriteRejectedException::class.java) {
            runBlocking { fake.create(conn, family, draft("Sleepover"), key) }
        }
        assertThat(fake.familyEvents().map { it.title }).doesNotContain("Sleepover")
    }
}
