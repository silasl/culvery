package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

@RunWith(AndroidJUnit4::class)
class CalendarRepositoryTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var repo: CalendarRepository
    private lateinit var alex: Person
    private lateinit var sam: Person

    private val london = ZoneId.of("Europe/London")
    private val window = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 15), london)
    private fun sept(day: Int) = LocalDate.of(2026, 9, day)

    @Before
    fun setUp() = runTest {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        repo = CalendarRepository(store, household, HouseholdZone(household), emptySet(), setOf(ScriptedWriter("calendar.test")))
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        sam = household.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        store.addConnection(
            Connection("c1", "calendar.test", "Google", emptyMap()),
            listOf(CalendarSource("s-alex", "Alex", writable = false), CalendarSource("s-family", "Family", writable = false)),
            mapOf("s-alex" to SourceMapping(alex.id, visible = true)),
        )
        store.setMaster("c1", "s-family")
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private fun timed(title: String, day: Int, hour: Int, minute: Int, minutes: Long, forPerson: String? = null): RemoteEvent {
        val start = sept(day).atTime(hour, minute).atZone(london).toInstant()
        return RemoteEvent(title, title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(minutes * 60)), recurring = false, forPerson = forPerson)
    }

    private fun allDay(title: String, fromDay: Int, toDayExclusive: Int) =
        RemoteEvent(title, title, EventTime.AllDay(sept(fromDay)), EventTime.AllDay(sept(toDayExclusive)), recurring = false)

    private fun local(title: String, start: LocalDateTime, end: LocalDateTime) = RemoteEvent(
        title, title,
        EventTime.Timed(start.atZone(london).toInstant()),
        EventTime.Timed(end.atZone(london).toInstant()),
        recurring = false,
    )

    private fun allDayOn(title: String, date: LocalDate) =
        RemoteEvent(title, title, EventTime.AllDay(date), EventTime.AllDay(date.plusDays(1)), recurring = false)

    private suspend fun titlesAndLabels(start: LocalDate, count: Int) =
        repo.days(start, count).first().map { d -> d.events.map { "${it.title} ${it.timeLabel}" } }

    private suspend fun put(sourceId: String, vararg events: RemoteEvent) =
        store.applySync("c1", sourceId, window, SyncResult(events.toList(), emptyList(), null, fullReplace = true))

    @Test
    fun dayListsEventsInStartOrderWithLabelsAndPeople() = runTest {
        put("s-alex", timed("Dinner with Jo & Priya", 23, 19, 30, 90))
        put("s-family", timed("School run", 23, 7, 45, 45, forPerson = sam.id.value))
        val day = repo.day(sept(23)).first()
        assertThat(day.map { Triple(it.title, it.timeLabel, it.person.name) }).containsExactly(
            Triple("School run", "07:45–08:30", "Sam"),
            Triple("Dinner with Jo & Priya", "19:30–21:00", "Alex"),
        ).inOrder()
        assertThat(day.map { it.startLabel }).containsExactly("07:45", "19:30").inOrder()
    }

    @Test
    fun untaggedEventOnAnUnmappedSourceIsFamily() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        assertThat(repo.day(sept(23)).first().single().person).isEqualTo(Person.Family)
    }

    @Test
    fun allDayEventsComeFirstAndSayAllDay() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60), allDay("Bin day", 23, 24))
        val day = repo.day(sept(23)).first()
        assertThat(day.map { it.title }).containsExactly("Bin day", "Boiler service").inOrder()
        assertThat(day.first().timeLabel).isEqualTo("All day")
        assertThat(day.first().startLabel).isEqualTo("All day")
        assertThat(day.first().allDay).isTrue()
    }

    @Test
    fun multiDayAllDayEventAppearsOnEveryDayItCovers() = runTest {
        put("s-family", allDay("Half term", 21, 25))
        val days = repo.days(sept(20), 6).first()
        assertThat(days.map { d -> d.date.dayOfMonth to d.events.map { it.title } }).containsExactly(
            20 to emptyList<String>(),
            21 to listOf("Half term"),
            22 to listOf("Half term"),
            23 to listOf("Half term"),
            24 to listOf("Half term"),
            25 to emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun multiDayEventStartingBeforeTheQueryShowsOnEveryVisibleDay() = runTest {
        put("s-family", allDay("Half term", 21, 25))
        val days = repo.days(sept(22), 4).first()
        assertThat(days.map { d -> d.date.dayOfMonth to d.events.map { it.title } }).containsExactly(
            22 to listOf("Half term"),
            23 to listOf("Half term"),
            24 to listOf("Half term"),
            25 to emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun overnightEventReadsFromThenUntil() = runTest {
        put("s-alex", timed("Night shift", 23, 22, 0, 180))
        val days = repo.days(sept(23), 2).first()
        assertThat(days.map { d -> d.events.map { "${it.timeLabel} / ${it.startLabel}" } }).containsExactly(
            listOf("22:00– / 22:00–"),
            listOf("until 01:00 / until 01:00"),
        ).inOrder()
    }

    @Test
    fun threeDayTimedEventReadsFromThenAllDayThenUntil() = runTest {
        // Monday 21 September 09:00 to Wednesday 23 September 17:00.
        put("s-alex", timed("Conference", 21, 9, 0, (2 * 24 + 8) * 60L))
        put("s-family", timed("Breakfast", 22, 8, 0, 60))
        assertThat(titlesAndLabels(sept(21), 4)).containsExactly(
            listOf("Conference 09:00–"),
            listOf("Conference All day", "Breakfast 08:00–09:00"),
            listOf("Conference until 17:00"),
            emptyList<String>(),
        ).inOrder()
        assertThat(repo.day(sept(22)).first().first().allDay).isTrue()
    }

    @Test
    fun recurringFlagReachesTheUi() = runTest {
        put("s-family", timed("Swimming", 23, 16, 0, 60).copy(recurring = true), timed("Boiler service", 23, 10, 0, 60))
        assertThat(repo.day(sept(23)).first().associate { it.title to it.recurring })
            .containsExactly("Boiler service", false, "Swimming", true)
    }

    @Test
    fun autumnClockChangeKeepsEventsOnTheirDayWithLocalLabels() = runTest {
        // Clocks go back at 02:00 BST on Sunday 25 October 2026.
        val change = LocalDate.of(2026, 10, 25)
        put("s-family", allDayOn("Clocks back", change), local("Night feed", change.atTime(0, 30), change.atTime(3, 0)))
        assertThat(titlesAndLabels(change.minusDays(1), 3)).containsExactly(
            emptyList<String>(),
            listOf("Clocks back All day", "Night feed 00:30–03:00"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun springClockChangeKeepsEventsOnTheirDayWithLocalLabels() = runTest {
        // Clocks go forward at 01:00 GMT on Sunday 28 March 2027.
        val change = LocalDate.of(2027, 3, 28)
        put("s-family", allDayOn("Clocks forward", change), local("Night feed", change.atTime(0, 30), change.atTime(3, 0)))
        assertThat(titlesAndLabels(change.minusDays(1), 3)).containsExactly(
            emptyList<String>(),
            listOf("Clocks forward All day", "Night feed 00:30–03:00"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun eventsUseTheHouseholdZone() = runTest {
        household.setLocation(HomeLocation("Tokyo", 35.68, 139.69, "Asia/Tokyo"))
        val start = Instant.parse("2026-09-23T00:30:00Z")
        put("s-alex", RemoteEvent("x", "Breakfast", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3600)), recurring = false))
        assertThat(repo.day(sept(23)).first().single().timeLabel).isEqualTo("09:30–10:30")
    }

    @Test
    fun weekHasSevenDaysAndALegendOfPeopleThenFamily() = runTest {
        val week = repo.week(sept(23)).first()
        assertThat(week.start).isEqualTo(sept(23))
        assertThat(week.days.map { it.date }).isEqualTo((0L..6L).map { sept(23).plusDays(it) })
        assertThat(week.people.map { it.name }).containsExactly("Alex", "Sam", "Family").inOrder()
    }

    @Test
    fun hasConnectionsFollowsTheStore() = runTest {
        val emptyDb = calendarDb()
        val emptyStore = CalendarStore(emptyDb)
        val emptyRepo = CalendarRepository(emptyStore, household, HouseholdZone(household), emptySet(), emptySet())
        assertThat(emptyRepo.hasConnections.first()).isFalse()
        emptyStore.addConnection(Connection("c9", "calendar.test", "Other", emptyMap()), emptyList(), emptyMap())
        assertThat(emptyRepo.hasConnections.first()).isTrue()
        emptyDb.close()
    }

    @Test
    fun syncStatusUsesTheStalestConnectionAndListsThoseNeedingSignIn() = runTest {
        store.addConnection(Connection("c2", "calendar.test", "School", emptyMap()), emptyList(), emptyMap())
        store.markSynced("c1", 5_000L)
        store.markSynced("c2", 2_000L)
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 0L)
        val status = repo.syncStatus.first()
        assertThat(status.lastSyncMillis).isEqualTo(2_000L)
        assertThat(status.needsSignIn).containsExactly("Google")
        assertThat(status.connectionLabels).containsExactly("Google", "School").inOrder()
        assertThat(status.failingBeforeFirstSync).isFalse()
    }

    @Test
    fun aConnectionFailingBeforeItsFirstSyncMakesTheStatusStale() = runTest {
        store.addConnection(Connection("c2", "calendar.test", "School", emptyMap()), emptyList(), emptyMap())
        store.markSynced("c1", 5_000L)
        store.setHealth("c2", ConnectionHealth.Unreachable, 0L)
        val status = repo.syncStatus.first()
        assertThat(status.lastSyncMillis).isEqualTo(5_000L)
        assertThat(status.failingBeforeFirstSync).isTrue()
        assertThat(status.isStaleAt(5_000L)).isTrue()
    }

    private fun ref(id: String, source: String = "s-family") = EventRef("c1", source, id)

    private val key = "0123456789abcdef0123456789abcdef"

    private suspend fun queue(
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft? = null,
        source: String = "s-family",
        clientKey: String? = null,
    ): Long = store.enqueue(
        PendingChange(0, "c1", source, remoteId, kind, draft, attempts = 1, nextAttemptMillis = 0, createdMillis = 0, clientKey = clientKey),
    )

    private suspend fun today() = repo.day(sept(23)).first()

    @Test
    fun eventsCarryTheirRefSourceAndConnection() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        val e = today().single()
        assertThat(e.ref).isEqualTo(ref("Boiler service"))
        assertThat(e.sourceName).isEqualTo("Family")
        assertThat(e.connectionLabel).isEqualTo("Google")
    }

    @Test
    fun masterEventsAreEditableAndOthersSayWhy() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60), timed("Swimming", 23, 16, 0, 60).copy(recurring = true))
        put("s-alex", timed("Dinner", 23, 19, 0, 60), timed("Gym", 23, 7, 0, 60).copy(recurring = true))
        val byTitle = today().associateBy { it.title }
        assertThat(byTitle.getValue("Boiler service").readOnlyReason).isNull()
        assertThat(byTitle.getValue("Boiler service").editable).isTrue()
        assertThat(byTitle.getValue("Swimming").readOnlyReason).isEqualTo(ReadOnlyReason.Recurring)
        assertThat(byTitle.getValue("Dinner").readOnlyReason).isEqualTo(ReadOnlyReason.OtherCalendar)
        assertThat(byTitle.getValue("Gym").readOnlyReason).isEqualTo(ReadOnlyReason.OtherCalendar)
    }

    @Test
    fun aMasterWhoseProviderHasNoWriterIsReadOnly() = runTest {
        val noWriter = CalendarRepository(store, household, HouseholdZone(household), emptySet(), emptySet())
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        assertThat(noWriter.day(sept(23)).first().single().readOnlyReason).isEqualTo(ReadOnlyReason.OtherCalendar)
    }

    @Test
    fun createdByNamesThePersonThePhoneOrTheFeed() = runTest {
        put(
            "s-family",
            timed("Dinner with Jo & Priya", 23, 19, 30, 90).copy(createdBy = alex.id.value),
            timed("Plumber quote call", 23, 13, 0, 30),
        )
        put("s-alex", timed("Gym", 23, 7, 0, 60))
        val byTitle = today().associateBy { it.title }
        assertThat(byTitle.getValue("Dinner with Jo & Priya").createdBy).isEqualTo("Alex")
        assertThat(byTitle.getValue("Plumber quote call").createdBy).isEqualTo(ADDED_FROM_PHONE)
        assertThat(byTitle.getValue("Gym").createdBy).isEqualTo(CALENDAR_FEED)
    }

    @Test
    fun untaggedMeansAMasterEventWithNoTagsAtAll() = runTest {
        put(
            "s-family",
            timed("Plumber quote call", 23, 13, 0, 30),
            timed("Assigned", 23, 14, 0, 30, forPerson = sam.id.value),
        )
        put("s-alex", timed("Gym", 23, 7, 0, 60))
        val byTitle = today().associateBy { it.title }
        assertThat(byTitle.getValue("Plumber quote call").untagged).isTrue()
        assertThat(byTitle.getValue("Assigned").untagged).isFalse()
        assertThat(byTitle.getValue("Gym").untagged).isFalse()
    }

    @Test
    fun aQueuedDeleteIsHidden() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        queue(ChangeKind.DELETE, "Boiler service")
        assertThat(today()).isEmpty()
        assertThat(store.eventNow(ref("Boiler service"))).isNotNull()
    }

    @Test
    fun aRejectedQueuedDeleteBringsTheEventBack() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        val id = queue(ChangeKind.DELETE, "Boiler service")
        assertThat(today()).isEmpty()
        // What the drain does when the provider refuses the delete.
        store.dropChange(id)
        assertThat(today().map { it.title to it.syncing }).containsExactly("Boiler service" to false)
    }

    @Test
    fun pendingDeleteStaysHiddenAfterAFullReplaceSync() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        queue(ChangeKind.DELETE, "Boiler service")
        put("s-family", timed("Boiler service", 23, 10, 0, 60), timed("Walk", 23, 12, 0, 30))
        assertThat(today().map { it.title }).containsExactly("Walk")
    }

    @Test
    fun pendingAssignShowsTheNewPersonAsSyncingAfterAFullReplaceSync() = runTest {
        val original = timed("Plumber quote call", 23, 13, 0, 30)
        put("s-family", original)
        queue(
            ChangeKind.ASSIGN,
            "Plumber quote call",
            EventDraft(original.title, original.start, original.end, forPerson = sam.id.value, createdBy = null),
        )
        assertThat(today().single().let { it.person.name to it.syncing }).isEqualTo("Sam" to true)
        put("s-family", original)
        assertThat(today().single().let { it.person.name to it.syncing }).isEqualTo("Sam" to true)
        assertThat(today().single().untagged).isFalse()
    }

    @Test
    fun aQueuedCreateShowsAsSyncingUnderItsKey() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        queue(ChangeKind.CREATE, null, EventDraft("Sleepover", slot.start, slot.end, forPerson = sam.id.value, createdBy = sam.id.value), clientKey = key)
        val e = today().single()
        assertThat(listOf(e.title, e.person.name)).containsExactly("Sleepover", "Sam").inOrder()
        assertThat(e.syncing).isTrue()
        assertThat(e.ref).isEqualTo(ref(key))
    }

    @Test
    fun aQueuedCreateOpensInTheDetailSheetAsSyncingAndEditable() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        queue(ChangeKind.CREATE, null, EventDraft("Sleepover", slot.start, slot.end, sam.id.value, sam.id.value), clientKey = key)
        val detail = repo.event(ref(key), today = sept(23)).first()!!
        assertThat(detail.whenLabel).isEqualTo("Today · 18:00–19:00")
        assertThat(detail.event.syncing).isTrue()
        assertThat(detail.event.editable).isTrue()
        assertThat(detail.event.createdBy).isEqualTo("Sam")
    }

    @Test
    fun aChangeAndADeleteQueuedBehindACreateShowUnderItsRef() = runTest {
        val slot = timed("Sleepover", 23, 18, 0, 60)
        val draft = EventDraft("Sleepover", slot.start, slot.end, sam.id.value, sam.id.value)
        queue(ChangeKind.CREATE, null, draft, clientKey = key)
        queue(ChangeKind.UPDATE, key, draft.copy(title = "Sleepover at Ava's"))
        assertThat(today().single().let { it.ref to it.title }).isEqualTo(ref(key) to "Sleepover at Ava's")
        assertThat(repo.event(ref(key), today = sept(23)).first()!!.event.title).isEqualTo("Sleepover at Ava's")
        queue(ChangeKind.DELETE, key)
        assertThat(today()).isEmpty()
        assertThat(repo.event(ref(key), today = sept(23)).first()).isNull()
    }

    @Test
    fun editableIsTheEventAsShownWithItsQueuedChanges() = runTest {
        val original = timed("Dinner with Jo & Priya", 23, 19, 30, 90, forPerson = alex.id.value)
        put("s-family", original)
        assertThat(repo.editable(ref(original.remoteId)).first())
            .isEqualTo(EditableEvent(ref(original.remoteId), original.title, original.start, original.end, alex.id.value))
        val moved = timed("Dinner at Gran's", 24, 18, 0, 60)
        queue(ChangeKind.UPDATE, original.remoteId, EventDraft(moved.title, moved.start, moved.end, sam.id.value, alex.id.value))
        assertThat(repo.editable(ref(original.remoteId)).first())
            .isEqualTo(EditableEvent(ref(original.remoteId), "Dinner at Gran's", moved.start, moved.end, sam.id.value))
        queue(ChangeKind.DELETE, original.remoteId)
        assertThat(repo.editable(ref(original.remoteId)).first()).isNull()
    }

    @Test
    fun theMasterServiceNamesWhereNewEventsGo() = runTest {
        assertThat(repo.masterService.first()).isEqualTo("Google")
    }

    @Test
    fun thereIsNowhereToAddWithoutAWriterForTheMaster() = runTest {
        val readOnly = CalendarRepository(store, household, HouseholdZone(household), emptySet(), emptySet())
        assertThat(readOnly.masterService.first()).isNull()
    }

    @Test
    fun detailHasTheWhenLabelAndFollowsTheOutbox() = runTest {
        put("s-family", timed("Dinner with Jo & Priya", 23, 19, 30, 90).copy(createdBy = alex.id.value))
        val detail = repo.event(ref("Dinner with Jo & Priya"), today = sept(23)).first()!!
        assertThat(detail.whenLabel).isEqualTo("Today · 19:30–21:00")
        assertThat(detail.event.createdBy).isEqualTo("Alex")
        assertThat(detail.event.editable).isTrue()
        queue(ChangeKind.DELETE, "Dinner with Jo & Priya")
        assertThat(repo.event(ref("Dinner with Jo & Priya"), today = sept(23)).first()).isNull()
    }

    @Test
    fun detailShowsAQueuedAssignAsSyncing() = runTest {
        val original = timed("Plumber quote call", 23, 13, 0, 30)
        put("s-family", original)
        queue(ChangeKind.ASSIGN, original.remoteId, EventDraft(original.title, original.start, original.end, sam.id.value, null))
        val detail = repo.event(ref(original.remoteId), today = sept(23)).first()!!
        assertThat(detail.event.syncing).isTrue()
        assertThat(detail.event.person.name).isEqualTo("Sam")
    }

    @Test
    fun detailOfAMissingEventIsNull() = runTest {
        assertThat(repo.event(ref("nope"), today = sept(23)).first()).isNull()
    }

    @Test
    fun anEventEndingAtMidnightStaysOnItsOwnDay() = runTest {
        put("s-alex", timed("Late film", 23, 22, 0, 120))
        assertThat(titlesAndLabels(sept(23), 2)).containsExactly(
            listOf("Late film 22:00–00:00"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun anEventEndingAtMidnightTwoDaysOnReadsFromThenAllDay() = runTest {
        put("s-alex", timed("Festival", 23, 22, 0, 26 * 60L))
        assertThat(titlesAndLabels(sept(23), 3)).containsExactly(
            listOf("Festival 22:00–"),
            listOf("Festival All day"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun peopleListsTheHousehold() = runTest {
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun eventsNameTheirServiceAndSayHowTheyRepeat() = runTest {
        val google = ScriptedProvider("calendar.test", displayName = "Google Calendar")
        val named = CalendarRepository(store, household, HouseholdZone(household), setOf(google), setOf(ScriptedWriter("calendar.test")))
        put("s-family", timed("Piano", 23, 15, 30, 60).copy(recurring = true, recurrenceRule = "RRULE:FREQ=WEEKLY"))
        val piano = named.day(LocalDate.of(2026, 9, 23)).first().single { it.title == "Piano" }
        assertThat(piano.serviceName).isEqualTo("Google Calendar")
        assertThat(piano.connectionLabel).isEqualTo("Google")
        assertThat(piano.repeats).isEqualTo("Every week")
        assertThat(named.masterService.first()).isEqualTo("Google Calendar")
    }
}
