package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.SourceGoneException
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection

@RunWith(AndroidJUnit4::class)
class GoogleReadTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val range = DateRange(today.minusDays(1), today.plusDays(15), london)
    private val google = FakeGoogleServer(london)
    private lateinit var provider: GoogleCalendarProvider
    private val conn = Connection("g1", GOOGLE_PROVIDER_ID, "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    // Named after the account, as a primary calendar usually is: the log checks catch its id or name in any log line.
    private val family = CalendarSource("family@example.com", "family@example.com", writable = true, primary = true)

    @Before
    fun setUp() {
        provider = testProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()))
        google.addCalendar(family.id, family.name, primary = true)
    }

    @After
    fun tearDown() {
        google.shutdown()
        assertLogsHoldNoPersonalData("Mia's swimming", "piano")
    }

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant = LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(london).toInstant()

    @Test
    fun calendarsAreEveryPageWithTheirNamesRightsTicksAndThePrimary() = runTest {
        google.pageSize = 1
        google.addCalendar("mia@group", "Mia", accessRole = "writer", summaryOverride = "Mia's swimming")
        google.addCalendar("holidays@group", "UK holidays", accessRole = "reader", selected = false)
        // Hidden in Google: still a source (hidden on the tablet), so its mapping survives being unhidden.
        google.addCalendar("club@group", "Old club", accessRole = "reader", hidden = true)
        assertThat(provider.sources(conn)).containsExactly(
            CalendarSource(family.id, family.name, writable = true, shown = true, primary = true),
            CalendarSource("mia@group", "Mia's swimming", writable = true, shown = true, primary = false),
            CalendarSource("holidays@group", "UK holidays", writable = false, shown = false, primary = false),
            CalendarSource("club@group", "Old club", writable = false, shown = false, primary = false),
        ).inOrder()
        val asked = google.requests.first().requestUrl!!
        assertThat(asked.queryParameter("showHidden") to asked.queryParameter("minAccessRole")).isEqualTo("true" to "reader")
    }

    @Test
    fun aCalendarThatShowsOnlyFreeBusyIsNotASource() = runTest {
        google.addCalendar("busy@group", "Busy", accessRole = "freeBusyReader")
        assertThat(provider.sources(conn).map { it.id }).containsExactly(family.id)
    }

    @Test
    fun aCalendarListWithNoItemsOrNoPrimaryIsUnreachableNotEveryCalendarGone() = runTest {
        google.failNextWith(MockResponse().setBody("{}"))
        assertThat(failureOf { provider.sources(conn) }).isInstanceOf(UnreachableException::class.java)
        google.failNextWith(MockResponse().setBody("""{"items":[{"id":"mia@group","summary":"Mia","accessRole":"owner"}]}"""))
        assertThat(failureOf { provider.sources(conn) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun anEventsPageWithNeitherTokenIsUnreachable() = runTest {
        google.failNextWith(MockResponse().setBody("{}"))
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aFullSyncAsksForTheWindowInTheHouseholdZoneFollowsPagesAndReturnsTheSyncToken() = runTest {
        google.pageSize = 2
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        google.putEvent(family.id, google.timed("b", "Swim", at(23, 16), at(23, 17)))
        google.putEvent(family.id, google.allDay("c", "Bin day", today, today.plusDays(1)))
        val result = provider.sync(conn, family, range, null)
        assertThat(result.upserts.map { it.remoteId }).containsExactly("a", "b", "c")
        assertThat(result.fullReplace).isTrue()
        assertThat(result.cursor).isNotNull()
        val first = google.requests.first().requestUrl!!
        assertThat(first.queryParameter("timeMin")).isEqualTo("2026-09-21T23:00:00Z")
        // The window's exclusive end, 8 October, starts at midnight BST: 23:00 UTC on the 7th.
        assertThat(first.queryParameter("timeMax")).isEqualTo("2026-10-07T23:00:00Z")
        assertThat(first.queryParameter("singleEvents")).isEqualTo("true")
        assertThat(google.requests).hasSize(2)
    }

    @Test
    fun anIncrementalSyncSendsOnlyItsTokenAndReturnsTheChanges() = runTest {
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        val first = provider.sync(conn, family, range, null)
        google.putEvent(family.id, google.timed("a", "Long walk", at(23, 9), at(23, 11)))
        google.putEvent(family.id, google.timed("b", "Swim", at(23, 16), at(23, 17)))
        google.cancel(family.id, "b")
        val next = provider.sync(conn, family, range, first.cursor)
        val url = google.requests.last().requestUrl!!
        assertThat(url.queryParameter("syncToken")).isEqualTo(first.cursor!!.value)
        assertThat(url.queryParameter("timeMin")).isNull()
        assertThat(next.fullReplace).isFalse()
        assertThat(next.upserts.map { it.title }).containsExactly("Long walk")
        assertThat(next.removedIds).containsExactly("b")
    }

    @Test
    fun anExpiredSyncTokenGivesAFullReplace() = runTest {
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        val first = provider.sync(conn, family, range, null)
        google.putEvent(family.id, google.timed("b", "Swim", at(23, 16), at(23, 17)))
        google.oldestValidToken = Long.MAX_VALUE
        val next = provider.sync(conn, family, range, first.cursor)
        assertThat(next.fullReplace).isTrue()
        assertThat(next.upserts.map { it.remoteId }).containsExactly("a", "b")
        val (expired, retry) = google.requests.takeLast(2).map { it.requestUrl!! }
        assertThat(expired.queryParameter("syncToken")).isEqualTo(first.cursor!!.value)
        assertThat(retry.queryParameter("syncToken")).isNull()
        assertThat(retry.queryParameter("timeMin")).isNotNull()
        assertThat(next.cursor).isEqualTo(SyncCursor(google.currentSyncToken()))
        assertThat(next.cursor).isNotEqualTo(first.cursor)
    }

    @Test
    fun anEventReadsItsTimesTagsAndTitle() = runTest {
        google.putEvent(
            family.id,
            google.timed("a", null, at(23, 19, 30), at(23, 21)) {
                putJsonObject("extendedProperties") { putJsonObject("private") { put(PERSON_KEY, "mia-id"); put(CREATED_BY_KEY, "sam-id") } }
            },
        )
        val event = provider.sync(conn, family, range, null).upserts.single()
        assertThat(event.title).isEqualTo("(No title)")
        assertThat(event.start).isEqualTo(EventTime.Timed(at(23, 19, 30)))
        assertThat(event.forPerson to event.createdBy).isEqualTo("mia-id" to "sam-id")
        assertThat(event.recurring).isFalse()
    }

    @Test
    fun anEventWhoseTimeDoesntParseIsLeftOutAndSaidSo() = runTest {
        google.putEvent(family.id, google.timed("a", "Walk", at(23, 9), at(23, 10)))
        val first = provider.sync(conn, family, range, null)
        // Handed over by an incremental sync: the fake's window check can't place an end it can't parse either.
        google.putEvent(family.id, google.timed("b", "Swim", at(23, 16), at(23, 17)) { putJsonObject("end") { put("dateTime", "half past four") } })
        assertThat(provider.sync(conn, family, range, first.cursor).upserts).isEmpty()
        assertLogsHoldNoPersonalData("half past four", "Swim", minLines = 1)
    }

    @Test
    fun googlesOffsetsAndDatesAreRead() {
        assertThat(GoogleTime(dateTime = "2026-09-23T19:30:00+01:00").toEventTime()).isEqualTo(EventTime.Timed(at(23, 19, 30)))
        assertThat(GoogleTime(date = "2026-09-23").toEventTime()).isEqualTo(EventTime.AllDay(today))
        assertThat(GoogleTime().toEventTime()).isNull()
    }

    @Test
    fun aWorkingLocationIsSkippedInAFullSyncAndRemovedInAnIncrementalOne() = runTest {
        google.putEvent(family.id, google.allDay("office", "Office", today, today.plusDays(1)) { put("eventType", "workingLocation") })
        val first = provider.sync(conn, family, range, null)
        assertThat(first.upserts).isEmpty()
        google.putEvent(family.id, google.allDay("office", "Office", today, today.plusDays(1)) { put("eventType", "workingLocation") })
        assertThat(provider.sync(conn, family, range, first.cursor).removedIds).containsExactly("office")
    }

    @Test
    fun instancesOfASeriesAreRecurringWithTheSeriesRuleFetchedOnce() = runTest {
        google.putEvent(family.id, google.timed("piano", "Piano", at(22, 15), at(22, 16)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY;BYDAY=TU") } })
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(22, 15), at(22, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("piano_2", "Piano", at(29, 15), at(29, 16)) { put("recurringEventId", "piano") })
        val upserts = provider.sync(conn, family, range, null).upserts
        assertThat(upserts.map { it.recurring to it.recurrenceRule })
            .containsExactly(true to "RRULE:FREQ=WEEKLY;BYDAY=TU", true to "RRULE:FREQ=WEEKLY;BYDAY=TU")
        assertThat(google.requests.count { it.requestUrl!!.pathSegments.last() == "piano" }).isEqualTo(1)
        // A full sync starts the cache again.
        provider.sync(conn, family, range, null)
        assertThat(google.requests.count { it.requestUrl!!.pathSegments.last() == "piano" }).isEqualTo(2)
    }

    @Test
    fun aFullSyncCancelledPartWayKeepsTheRulesItFetchedForTheNextAttempt() = runTest {
        google.putEvent(family.id, google.timed("piano", "Piano", at(22, 15), at(22, 16)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY") } })
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(22, 15), at(22, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("swim", "Swim", at(23, 17), at(23, 18)) { putJsonArray("recurrence") { add("RRULE:FREQ=DAILY") } })
        google.putEvent(family.id, google.timed("swim_1", "Swim", at(23, 17), at(23, 18)) { put("recurringEventId", "swim") })
        fun asksFor(series: String) = google.requests.count { it.requestUrl!!.pathSegments.last() == series }
        // Swim's rule never comes back, so the pass is cancelled after piano's was fetched, as a timeout would.
        google.failNextWith(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) { it.requestUrl!!.pathSegments.last() == "swim" }
        val first = launch(Dispatchers.Default) { provider.sync(conn, family, range, null) }
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (asksFor("swim") == 0) delay(10) } }
        first.cancelAndJoin()
        val upserts = provider.sync(conn, family, range, null).upserts
        assertThat(upserts.map { it.recurrenceRule }).containsExactly("RRULE:FREQ=WEEKLY", "RRULE:FREQ=DAILY")
        assertThat(asksFor("piano")).isEqualTo(1)
        assertThat(asksFor("swim")).isEqualTo(2)
    }

    @Test
    fun aSeriesWhoseRuleCantBeReadStillSyncsWithNoRuleAndIsAskedForOncePerSync() = runTest {
        // The series is there, so the fetch really meets the 500 rather than a 404.
        google.putEvent(family.id, google.timed("piano", "Piano", at(22, 15), at(22, 16)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY") } })
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(22, 15), at(22, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("piano_2", "Piano", at(29, 15), at(29, 16)) { put("recurringEventId", "piano") })
        google.failNext(500) { it.requestUrl!!.pathSegments.last() == "piano" }
        val upserts = provider.sync(conn, family, range, null).upserts
        assertThat(upserts.map { it.recurring to it.recurrenceRule }).containsExactly(true to null, true to null)
        // The failure is remembered for the rest of this sync: the second instance doesn't ask again.
        assertThat(google.requests.count { it.requestUrl!!.pathSegments.last() == "piano" }).isEqualTo(1)
    }

    @Test
    fun anInvitationTheAccountDeclinedIsSkippedInAFullSyncAndRemovedInAnIncrementalOne() = runTest {
        fun invitation(answer: String) = google.timed("party", "Party", at(24, 19), at(24, 22)) {
            putJsonArray("attendees") {
                add(buildJsonObject { put("email", "family@example.com"); put("self", true); put("responseStatus", answer) })
                add(buildJsonObject { put("email", "host@example.com"); put("responseStatus", "accepted") })
            }
        }
        google.putEvent(family.id, invitation("needsAction"))
        val first = provider.sync(conn, family, range, null)
        assertThat(first.upserts.map { it.remoteId }).containsExactly("party")
        google.putEvent(family.id, invitation("declined"))
        assertThat(provider.sync(conn, family, range, first.cursor).removedIds).containsExactly("party")
        assertThat(provider.sync(conn, family, range, null).upserts).isEmpty()
    }

    @Test
    fun aDeletedOrForbiddenCalendarIsSourceGone() = runTest {
        google.failNext(404, "notFound")
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(SourceGoneException::class.java)
        google.failNext(403, "forbidden")
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(SourceGoneException::class.java)
    }

    @Test
    fun anyOtherRefusalOrAnUnreadableBodyIsUnreachable() = runTest {
        google.failNext(400, "invalid")
        assertThat(failureOf { provider.sync(conn, family, range, null) }).isInstanceOf(UnreachableException::class.java)
        google.failNextWith(MockResponse().setBody("not json"))
        assertThat(failureOf { provider.sync(conn, family, range, SyncCursor("t0")) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aConnectionWithNoAccountNeedsSigningIn() = runTest {
        assertThat(failureOf { provider.sources(conn.copy(config = emptyMap())) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun calendarListsAskOnlyForWhatTheTabletReads() = runTest {
        provider.sources(conn)
        assertThat(google.requests.first().requestUrl!!.queryParameter("fields")).isEqualTo(CALENDAR_LIST_FIELDS)
    }

    @Test
    fun fullAndIncrementalEventListsAskOnlyForWhatTheTabletReads() = runTest {
        val full = provider.sync(conn, family, range, cursor = null)
        provider.sync(conn, family, range, full.cursor)
        val lists = google.requests.map { it.requestUrl!! }.filter { it.encodedPath.endsWith("/events") }
        assertThat(lists).hasSize(2)
        assertThat(lists.map { it.queryParameter("fields") }).containsExactly(EVENT_FIELDS, EVENT_FIELDS)
    }

    /**
     * Ruling 4: a field the provider parses but doesn't ask for would come back missing, silently. Walks each answer's
     * classes, nested ones too, against Google's `fields` syntax: a field named alone comes whole; one with a
     * selection, `a(b,c)` or `a/b`, comes with only those.
     */
    @Test
    fun everyFieldTheProviderParsesIsAskedFor() {
        assertAskedFor(FieldsSelection.parse(EVENT_FIELDS), EventsPage.serializer().descriptor, "")
        assertAskedFor(FieldsSelection.parse(CALENDAR_LIST_FIELDS), CalendarListPage.serializer().descriptor, "")
    }

    private fun assertAskedFor(asked: FieldsSelection, descriptor: SerialDescriptor, path: String) {
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            assertWithMessage("$path$name isn't asked for").that(asked.fields).containsKey(name)
            val only = asked.fields[name] ?: continue
            var element = descriptor.getElementDescriptor(index)
            if (element.kind == StructureKind.LIST) element = element.getElementDescriptor(0)
            if (element.kind == StructureKind.CLASS) assertAskedFor(only, element, "$path$name.")
        }
    }
}

/** Google's `fields` syntax as a tree: each name maps to its own selection, or to null when it comes whole. */
private class FieldsSelection(val fields: Map<String, FieldsSelection?>) {
    companion object {
        fun parse(text: String): FieldsSelection = FieldsParser(text).parse()
    }
}

/** Reads `a,b(c,d),e/f`: a list of names, each with a bracketed list or a one-name path below it. */
private class FieldsParser(private val text: String) {
    private var i = 0

    fun parse(): FieldsSelection = list().also { check(i == text.length) { "unparsed fields text" } }

    private fun list(): FieldsSelection {
        val out = mutableMapOf<String, FieldsSelection?>()
        while (true) {
            val (name, only) = item()
            out[name] = only
            if (i < text.length && text[i] == ',') i++ else return FieldsSelection(out)
        }
    }

    private fun item(): Pair<String, FieldsSelection?> {
        val start = i
        while (i < text.length && text[i] !in ",()/") i++
        val name = text.substring(start, i)
        return name to when {
            i < text.length && text[i] == '(' -> {
                i++
                list().also {
                    check(text[i] == ')')
                    i++
                }
            }
            i < text.length && text[i] == '/' -> {
                i++
                FieldsSelection(mapOf(item()))
            }
            else -> null
        }
    }
}
