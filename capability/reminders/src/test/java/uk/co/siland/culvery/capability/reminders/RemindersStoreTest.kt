package uk.co.siland.culvery.capability.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.HouseBin
import uk.co.siland.culvery.core.household.PersonId

@RunWith(AndroidJUnit4::class)
class RemindersStoreTest {
    private lateinit var db: RemindersDatabase
    private lateinit var store: RemindersStore
    private val alex = PersonId("alex")
    private val sam = PersonId("sam")
    private val day = LocalDate.of(2026, 11, 20)
    private val zone = ZoneId.of("Europe/London")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RemindersDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RemindersStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun once(words: String = "PE kit", at: LocalTime = LocalTime.of(7, 30), on: LocalDate = day, who: List<PersonId> = listOf(alex)) =
        ReminderDraft(words, who, at, Schedule.Once(on))

    private fun weekly(vararg days: DayOfWeek) =
        ReminderDraft("Swimming bag", listOf(PersonId.FAMILY), LocalTime.of(8, 0), weeklyOn(*days))

    @Test
    fun aOneOffAndARepeatRoundTrip() = runTest {
        val a = store.add(once(who = listOf(alex, sam)), madeBy = sam, nowMillis = 1_000)!!
        val b = store.add(weekly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), madeBy = alex, nowMillis = 2_000)!!
        assertThat(store.reminders.first()).containsExactly(
            Reminder(a, "PE kit", listOf(alex, sam), sam, LocalTime.of(7, 30), Schedule.Once(day), 1_000, null, null),
            Reminder(
                b, "Swimming bag", listOf(PersonId.FAMILY), alex, LocalTime.of(8, 0),
                weeklyOn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), 2_000, null, null,
            ),
        )
    }

    @Test
    fun theFiftyFirstIsRefused() = runTest {
        repeat(REMINDERS_MAX) { assertThat(store.add(once(), alex, 0)).isNotNull() }
        assertThat(store.add(once(), alex, 0)).isNull()
        assertThat(store.reminders.first()).hasSize(REMINDERS_MAX)
    }

    @Test
    fun doneDeletesAOneOffAndRestartsARepeat() = runTest {
        val a = store.add(once(), alex, 0)!!
        val b = store.add(weekly(DayOfWeek.FRIDAY), alex, 0)!!
        store.later(b, 100)
        store.done(a, 5_000, zone)
        store.done(b, 6_000, zone)
        val left = store.reminders.first()
        assertThat(left.map { it.id }).containsExactly(b)
        assertThat(left.single().answeredAtMillis).isEqualTo(6_000)
        assertThat(left.single().snoozedUntilMillis).isNull()
    }

    @Test
    fun aChangeOfTimeDayOrRepeatResetsAnsweredAtAndClearsTheSnooze() = runTest {
        val changes = listOf(
            once(at = LocalTime.of(9, 0)),
            once(on = day.plusDays(1)),
            ReminderDraft("PE kit", listOf(alex), LocalTime.of(7, 30), weeklyOn(DayOfWeek.FRIDAY, from = LocalDate.of(2026, 11, 16))),
        )
        for (changed in changes) {
            val id = store.add(once(), alex, 1_000)!!
            store.later(id, 100)
            assertThat(store.change(id, changed, 9_000)).isTrue()
            val r = store.reminders.first().single { it.id == id }
            assertThat(r.answeredAtMillis).isEqualTo(9_000)
            assertThat(r.snoozedUntilMillis).isNull()
            store.delete(id)
        }
    }

    @Test
    fun aChangeOfDaysOnARepeatResetsAnsweredAtAndClearsTheSnooze() = runTest {
        val id = store.add(weekly(DayOfWeek.MONDAY), alex, 1_000)!!
        store.later(id, 100)
        assertThat(store.change(id, weekly(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 9_000)).isTrue()
        val r = store.reminders.first().single()
        assertThat(r.answeredAtMillis).isEqualTo(9_000)
        assertThat(r.snoozedUntilMillis).isNull()
    }

    @Test
    fun aChangeOfWordsOrWhoKeepsThem() = runTest {
        val id = store.add(once(), alex, 1_000)!!
        store.later(id, 200)
        assertThat(store.change(id, once(words = "Library book", who = listOf(sam)), 9_000)).isTrue()
        val r = store.reminders.first().single()
        assertThat(r.words).isEqualTo("Library book")
        assertThat(r.forPeople).containsExactly(sam)
        assertThat(r.madeBy).isEqualTo(alex)
        assertThat(r.answeredAtMillis).isEqualTo(1_000)
        assertThat(r.snoozedUntilMillis).isEqualTo(200 + LATER_MS)
    }

    @Test
    fun changingAGoneReminderSaysSo() = runTest {
        assertThat(store.change(404, once(), 0)).isFalse()
    }

    @Test
    fun laterIsThirtyMinutes() = runTest {
        val id = store.add(once(), alex, 0)!!
        store.later(id, 10_000)
        assertThat(store.reminders.first().single().snoozedUntilMillis).isEqualTo(10_000 + 1_800_000L)
    }

    @Test
    fun deletingTwiceSaysItHadGone() = runTest {
        val id = store.add(once(), alex, 0)!!
        assertThat(store.delete(id)).isTrue()
        assertThat(store.delete(id)).isFalse()
    }

    @Test
    fun chimedMarksTheRemindersAndTheBins() = runTest {
        val id = store.add(once(), alex, 0)!!
        store.chimed(listOf(id), bins = true, nowMillis = 7_000)
        assertThat(store.reminders.first().single().chimedAtMillis).isEqualTo(7_000)
        assertThat(store.state.first().binsChimedAtMillis).isEqualTo(7_000)
    }

    @Test
    fun aKeptCollectionRoundTripsWithItsBinsAndInks() = runTest {
        val kept = BinCollection(day, listOf(HouseBin("Food", "green"), HouseBin("Rubbish", null)))
        store.binsKept(kept)
        assertThat(store.state.first().binsKept).isEqualTo(kept)
        store.binsKept(null)
        assertThat(store.state.first().binsKept).isNull()
    }

    @Test
    fun theStateStartsSwitchedOnAtSix() = runTest {
        assertThat(store.state.first()).isEqualTo(ReminderState())
        assertThat(ReminderState().binsOn).isTrue()
        assertThat(ReminderState().binsTime).isEqualTo(LocalTime.of(18, 0))
    }

    @Test
    fun theBinsSettingsAndMarksAreStored() = runTest {
        store.setBins(false, LocalTime.of(17, 15))
        store.binsDone(day)
        store.binsLater(1_000)
        val s = store.state.first()
        assertThat(s.binsOn).isFalse()
        assertThat(s.binsTime).isEqualTo(LocalTime.of(17, 15))
        assertThat(s.binsDoneFor).isEqualTo(day)
        assertThat(s.binsSnoozedUntilMillis).isEqualTo(1_000 + LATER_MS)
    }

    @Test
    fun notHeldOnFreshStateStoresNoEnd() = runTest {
        store.markHeld(false, 1_000)
        assertThat(store.state.first().heldEndedAtMillis).isNull()
    }

    @Test
    fun markHeldStoresWhenHoldingEnds() = runTest {
        store.markHeld(true, 1_000)
        assertThat(store.state.first().heldSeen).isTrue()
        assertThat(store.state.first().heldEndedAtMillis).isNull()
        store.markHeld(false, 2_000)
        assertThat(store.state.first().heldSeen).isFalse()
        assertThat(store.state.first().heldEndedAtMillis).isEqualTo(2_000)
        store.markHeld(false, 3_000)
        assertThat(store.state.first().heldEndedAtMillis).isEqualTo(2_000)
    }

    @Test
    fun doneOnTheLastTimeDeletesARepeat() = runTest {
        val twoFridays = ReminderDraft("PE kit", listOf(alex), LocalTime.of(7, 30), Schedule.Repeating(day, "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=2"))
        val id = store.add(twoFridays, alex, 0)!!
        store.done(id, at("2026-11-20T08:00:00"), zone)
        assertThat(store.reminders.first().single().answeredAtMillis).isEqualTo(at("2026-11-20T08:00:00"))
        store.done(id, at("2026-11-27T08:00:00"), zone)
        assertThat(store.reminders.first()).isEmpty()
    }

    @Test
    fun aNewRuleRestartsTheReminder() = runTest {
        val id = store.add(weekly(DayOfWeek.FRIDAY), alex, 1_000)!!
        store.later(id, 100)
        val first = weeklyOn(DayOfWeek.FRIDAY).first
        val fortnightly = weekly(DayOfWeek.FRIDAY).copy(schedule = Schedule.Repeating(first, "RRULE:FREQ=WEEKLY;INTERVAL=2"))
        assertThat(store.change(id, fortnightly, 9_000)).isTrue()
        val r = store.reminders.first().single()
        assertThat(r.schedule).isEqualTo(fortnightly.schedule)
        assertThat(r.answeredAtMillis).isEqualTo(9_000)
        assertThat(r.snoozedUntilMillis).isNull()
    }

    @Test
    fun theLinkSurvivesAChangeOfWords() = runTest {
        val friday = weeklyOn(DayOfWeek.FRIDAY)
        val event = EventLink("event-1", friday.first, LocalTime.of(9, 0), friday.rule, followsRepeat = true)
        val draft = ReminderDraft("Swimming", listOf(alex), LocalTime.of(8, 0), friday, event)
        val id = store.add(draft, alex, 1_000)!!
        assertThat(store.reminders.first().single().event).isEqualTo(event)

        store.change(id, draft.copy(words = "Swimming bag"), 9_000)
        val renamed = store.reminders.first().single()
        assertThat(renamed.event).isEqualTo(event)
        assertThat(renamed.answeredAtMillis).isEqualTo(1_000)

        store.change(id, draft.copy(schedule = friday.copy(rule = "RRULE:FREQ=WEEKLY;INTERVAL=2")), 9_000)
        assertThat(store.reminders.first().single().event).isEqualTo(event.copy(followsRepeat = false))
    }
}
