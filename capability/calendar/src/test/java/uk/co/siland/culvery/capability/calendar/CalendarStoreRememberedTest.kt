package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.EventEntity
import uk.co.siland.culvery.capability.calendar.db.RememberedTitleEntity
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection

@RunWith(AndroidJUnit4::class)
class CalendarStoreRememberedTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val zone = TEST_ZONE
    private val today = TEST_TODAY
    private val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), zone)
    private val weekly = "DTSTART:20260905\nRRULE:FREQ=WEEKLY;INTERVAL=1"

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun at(date: LocalDate, hour: Int = 9): Instant = date.atTime(hour, 0).atZone(zone).toInstant()

    private fun sept(day: Int, year: Int = 2026) = LocalDate.of(year, 9, day)

    private fun timed(
        id: String,
        title: String,
        date: LocalDate,
        hour: Int = 9,
        minutes: Long = 60,
        rule: String? = null,
        seriesId: String? = null,
    ) = RemoteEvent(
        id, title, EventTime.Timed(at(date, hour)), EventTime.Timed(at(date, hour).plusSeconds(minutes * 60)),
        recurring = rule != null || seriesId != null, recurrenceRule = rule, seriesId = seriesId,
    )

    private fun allDay(id: String, title: String, from: LocalDate, to: LocalDate) =
        RemoteEvent(id, title, EventTime.AllDay(from), EventTime.AllDay(to), recurring = false)

    private fun full(vararg events: RemoteEvent) = SyncResult(events.toList(), emptyList(), SyncCursor("k1"), fullReplace = true)

    private fun changes(vararg events: RemoteEvent, removed: List<String> = emptyList()) =
        SyncResult(events.toList(), removed, SyncCursor("k2"), fullReplace = false)

    private suspend fun connect(id: String = "c1", sourceIds: List<String> = listOf("s1"), hidden: Set<String> = emptySet()) =
        store.addConnection(
            Connection(id, "calendar.test", id, emptyMap()),
            sourceIds.map { CalendarSource(it, it.uppercase(), writable = false) },
            sourceIds.associateWith { SourceMapping(PersonId.FAMILY, visible = it !in hidden) },
        )

    private suspend fun sync(source: String, result: SyncResult, connection: String = "c1") =
        store.applySync(connection, source, window, result, today)

    private suspend fun everything(): List<RememberedTitleEntity> = db.calendarDao().allRemembered().sortedBy { it.titleKey }

    private suspend fun keys() = everything().map { it.titleKey }

    // filling

    @Test
    fun aSyncRemembersWhatItRead() = runTest {
        connect()
        sync("s1", full(timed("swim", "Swim", sept(23), hour = 15), allDay("bins", "Bin day", sept(24), sept(25))))
        assertThat(store.rememberedTitles().map { it.title }).containsExactly("Swim", "Bin day")
        val swim = store.rememberedTitles().first { it.title == "Swim" }
        assertThat(listOf(swim.connectionId, swim.sourceId, swim.sourceEventId, swim.startMinute, swim.lengthMinutes))
            .containsExactly("c1", "s1", "swim", 15 * 60, 60).inOrder()
        assertThat(swim.rememberedDate).isEqualTo("2026-09-23")
    }

    @Test
    fun theSameEventReplacesItsOwnFields() = runTest {
        connect()
        sync("s1", full(timed("swim", "Swim", sept(23), hour = 9)))
        sync("s1", changes(timed("swim", "Swim", sept(23), hour = 10, minutes = 90)))
        val swim = store.rememberedTitles().single()
        assertThat(listOf(swim.startMinute, swim.lengthMinutes)).containsExactly(600, 90).inOrder()
    }

    @Test
    fun aChangedFutureInstanceKeepsTheSeriesLastDate() = runTest {
        connect()
        sync("s1", full(timed("swim_1", "Swim", sept(19), rule = weekly, seriesId = "swim")))
        sync("s1", changes(timed("swim_2", "Swim", sept(26), hour = 11, rule = weekly, seriesId = "swim")))
        val swim = store.rememberedTitles().single()
        assertThat(swim.rememberedDate).isEqualTo("2026-09-19")
        assertThat(swim.startMinute).isEqualTo(11 * 60)
        assertThat(swim.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
    }

    @Test
    fun aLaterPastDateWins() = runTest {
        connect()
        sync("s1", full(timed("a", "Swim", sept(10))))
        sync("s1", changes(timed("b", "swim", sept(20))))
        assertThat(store.rememberedTitles().single().let { it.sourceEventId to it.title }).isEqualTo("b" to "swim")
        sync("s1", changes(timed("c", "Swim", sept(15))))
        assertThat(store.rememberedTitles().single().sourceEventId).isEqualTo("b")
    }

    @Test
    fun aHiddenCalendarIsNotRemembered() = runTest {
        connect(sourceIds = listOf("s1", "s2"), hidden = setOf("s2"))
        sync("s2", full(timed("party", "Surprise party", sept(23))))
        assertThat(everything()).isEmpty()
    }

    @Test
    fun aCalendarHiddenLaterIsNotOffered() = runTest {
        connect()
        sync("s1", full(timed("swim", "Swim", sept(23))))
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        assertThat(store.rememberedTitles()).isEmpty()
    }

    @Test
    fun anUnreadableRuleIsRememberedWithoutItAndTheSyncLands() = runTest {
        connect()
        sync("s1", full(timed("odd_1", "Choir", sept(23), rule = "RRULE:FREQ", seriesId = "odd")))
        assertThat(store.rememberedTitles().single().rule).isNull()
        assertThat(store.eventNow(EventRef("c1", "s1", "odd_1"))).isNotNull()
    }

    // forgetting

    @Test
    fun aDeletedEventsTitleStaysWhileAnotherEventHasIt() = runTest {
        connect()
        sync("s1", full(timed("a", "Swim", sept(10)), timed("b", "Swim", sept(20))))
        assertThat(store.rememberedTitles().single().sourceEventId).isEqualTo("b")
        store.applyDeleted(EventRef("c1", "s1", "b"), today, zone)
        val swim = store.rememberedTitles().single()
        assertThat(swim.sourceEventId).isEqualTo("a")
        assertThat(swim.rememberedDate).isEqualTo("2026-09-10")
    }

    @Test
    fun aTitleWithNoEventLeftIsForgotten() = runTest {
        connect()
        sync("s1", full(timed("a", "Swim", sept(23)), timed("b", "Dentist", sept(24))))
        store.applyDeleted(EventRef("c1", "s1", "a"), today, zone)
        assertThat(keys()).containsExactly("dentist")
    }

    @Test
    fun aSeriesDeletedOnThePhoneIsForgotten() = runTest {
        connect()
        sync(
            "s1",
            full(timed("swim_1", "Swim", sept(19), rule = weekly, seriesId = "swim"), timed("swim_2", "Swim", sept(26), rule = weekly, seriesId = "swim")),
        )
        sync("s1", changes(removed = listOf("swim_1")))
        assertThat(keys()).containsExactly("swim")
        sync("s1", changes(removed = listOf("swim_2")))
        assertThat(keys()).isEmpty()
    }

    @Test
    fun oneCancelledOccurrenceKeepsTheSeriesTitle() = runTest {
        connect()
        sync(
            "s1",
            full(timed("swim_1", "Swim", sept(19), rule = weekly, seriesId = "swim"), timed("swim_2", "Swim", sept(26), rule = weekly, seriesId = "swim")),
        )
        sync("s1", changes(removed = listOf("swim_2")))
        assertThat(store.rememberedTitles().single().sourceEventId).isEqualTo("swim")
    }

    @Test
    fun undoingASeriesForgetsItsTitle() = runTest {
        connect()
        sync(
            "s1",
            full(
                timed("swim_1", "Swim", sept(19), rule = weekly, seriesId = "swim"),
                timed("swim", "Swim", sept(5), rule = weekly),
                timed("dentist", "Dentist", sept(24)),
            ),
        )
        assertThat(keys()).containsExactly("dentist", "swim")
        store.applySeriesDeleted("c1", "s1", "swim", today, zone)
        assertThat(keys()).containsExactly("dentist")
    }

    @Test
    fun aFullReplaceForgetsWhatItDrops() = runTest {
        connect()
        sync("s1", full(timed("a", "Swim", sept(23)), timed("b", "Dentist", sept(24))))
        sync("s1", full(timed("b", "Dentist", sept(24))))
        assertThat(keys()).containsExactly("dentist")
    }

    @Test
    fun aRenamedEventForgetsItsOldTitle() = runTest {
        connect()
        sync("s1", full(timed("a", "Swimn", sept(23))))
        sync("s1", changes(timed("a", "Swim", sept(23))))
        assertThat(everything().map { it.title }).containsExactly("Swim")
    }

    @Test
    fun aCalendarNoLongerListedForgetsItsTitles() = runTest {
        connect(sourceIds = listOf("s1", "s2"))
        sync("s1", full(timed("a", "Swim", sept(10)), timed("keep", "Dentist", sept(11))))
        sync("s2", full(timed("b", "Swim", sept(20)), timed("only", "Surprise", sept(21))))
        assertThat(keys()).containsExactly("dentist", "surprise", "swim")
        store.refreshSources("c1", listOf(CalendarSource("s1", "S1", writable = false, primary = true)), 5_000L, zone) { SourceMapping.Default }
        assertThat(keys()).containsExactly("dentist", "swim")
        assertThat(store.rememberedTitles().first { it.titleKey == "swim" }.sourceEventId).isEqualTo("a")
    }

    @Test
    fun removingAConnectionForgetsItsTitles() = runTest {
        connect("c1")
        connect("c2")
        sync("s1", full(timed("a", "Swim", sept(23))), "c1")
        sync("s1", full(timed("a", "Swim", sept(23))), "c2")
        store.removeConnection("c1")
        assertThat(everything().map { it.connectionId }).containsExactly("c2")
    }

    // age

    @Test
    fun aTitleOlderThanTwelveMonthsIsForgottenOnTheNextSync() = runTest {
        connect()
        val old = sept(22, year = 2025)
        db.calendarDao().upsertEvents(listOf(stored("ancient", "Ancient", old)))
        db.calendarDao().upsertRemembered(row("Ancient", "ancient", old))
        repeat(2) {
            store.seedRememberedTitles("c1", today, zone)
            sync("s1", changes())
            assertThat(keys()).isEmpty()
        }
    }

    // seeding

    @Test
    fun theFirstPassSeedsFromStoredEvents() = runTest {
        connect()
        db.calendarDao().upsertEvents(listOf(stored("swim", "Swim", sept(20)), stored("dentist", "Dentist", sept(24))))
        store.seedRememberedTitles("c1", today, zone)
        assertThat(keys()).containsExactly("dentist", "swim")
        db.calendarDao().upsertEvents(listOf(stored("later", "Later", sept(25))))
        store.seedRememberedTitles("c1", today, zone)
        assertThat(keys()).containsExactly("dentist", "swim")
    }

    @Test
    fun theSeedSkipsHiddenCalendars() = runTest {
        connect(sourceIds = listOf("s1", "s2"), hidden = setOf("s2"))
        db.calendarDao().upsertEvents(listOf(stored("swim", "Swim", sept(20)), stored("party", "Surprise", sept(20), source = "s2")))
        store.seedRememberedTitles("c1", today, zone)
        assertThat(keys()).containsExactly("swim")
    }

    @Test
    fun seedingDoesNotResurrectATitleOlderThanTwelveMonths() = runTest {
        connect()
        db.calendarDao().upsertEvents(listOf(stored("ancient", "Ancient", sept(22, year = 2025)), stored("swim", "Swim", sept(20))))
        store.seedRememberedTitles("c1", today, zone)
        assertThat(keys()).containsExactly("swim")
    }

    @Test
    fun aSeedAfterTheConnectionWentWritesNothing() = runTest {
        db.calendarDao().upsertEvents(listOf(stored("swim", "Swim", sept(20), connection = "ghost")))
        store.seedRememberedTitles("ghost", today, zone)
        assertThat(everything()).isEmpty()
    }

    private fun stored(id: String, title: String, date: LocalDate, source: String = "s1", connection: String = "c1"): EventEntity {
        val start = at(date).toEpochMilli()
        return EventEntity(connection, source, id, title, start, null, start + 3_600_000, null, false, null, null, start, start + 3_600_000)
    }

    private fun row(title: String, id: String, date: LocalDate) = RememberedTitleEntity(
        "c1", titleKey(title), title, "s1", id, "family", false, 540, 60, null, null, null, date.toString(),
    )
}
