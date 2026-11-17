package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.HouseBin

class BinsTimingTest {
    private val zone = ZoneId.of("Europe/London")
    private val thursday = LocalDate.of(2026, 11, 19)
    private val friday = LocalDate.of(2026, 11, 20)
    private val food = HouseBin("Food", "green")
    private val recycling = HouseBin("Recycling", null)
    private val thursdays = BinCollection(thursday, listOf(food, recycling))

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun days(vararg collections: BinCollection, connected: Boolean = true, readFailed: Boolean = false) =
        BinDays(connected, readFailed, collections.toList())

    private fun due(days: BinDays?, now: String, state: ReminderState = ReminderState()) = binsDue(days, state, at(now), zone)

    @Test
    fun waitingFromTheEveningBeforeUntilNoon() {
        val days = days(thursdays)
        assertThat(due(days, "2026-11-18T17:59:00")).isNull()
        assertThat(due(days, "2026-11-18T18:00:00")).isNotNull()
        assertThat(due(days, "2026-11-19T11:59:00")).isNotNull()
        assertThat(due(days, "2026-11-19T12:00:00")).isNull()
    }

    @Test
    fun tonightUntilMidnightThenToday() {
        assertThat(binsWords(thursday, LocalDate.of(2026, 11, 18))).isEqualTo("Bins out tonight")
        assertThat(binsWords(thursday, thursday)).isEqualTo("Bins out today")
        assertThat(BINS_WHO).isEqualTo("Bins")
    }

    @Test
    fun theDueCarriesItsKeyItsEveningAndTheStatesMarks() {
        val state = ReminderState(binsSnoozedUntilMillis = 5L, binsChimedAtMillis = 7L)
        val (due, collection) = due(days(thursdays), "2026-11-18T19:00:00", state)!!
        assertThat(due).isEqualTo(Due(DueKey.Bins(thursday), at("2026-11-18T18:00:00"), 5L, 7L))
        assertThat(collection).isEqualTo(thursdays)
    }

    @Test
    fun aCollectionSeenLateIsWaitingAtOnce() {
        assertThat(due(days(thursdays), "2026-11-19T09:00:00")!!.first.dueAtMillis).isEqualTo(at("2026-11-18T18:00:00"))
    }

    @Test
    fun switchedOnAfterTheTimeIsWaitingAtOnce() {
        val state = ReminderState(binsOn = true, binsTime = LocalTime.of(17, 0))
        assertThat(due(days(thursdays), "2026-11-18T20:00:00", state)).isNotNull()
    }

    @Test
    fun switchedOffIsNeverWaiting() {
        assertThat(due(days(thursdays), "2026-11-18T20:00:00", ReminderState(binsOn = false))).isNull()
    }

    @Test
    fun doneEndsItForThatCollectionOnly() {
        val both = days(thursdays, BinCollection(friday, listOf(food)))
        val done = ReminderState(binsDoneFor = thursday)
        assertThat(due(both, "2026-11-18T20:00:00", done)).isNull()
        assertThat(due(both, "2026-11-19T20:00:00", done)!!.first.key).isEqualTo(DueKey.Bins(friday))
    }

    @Test
    fun movingTheTimeLaterTakesItDownUntilThen() {
        val later = ReminderState(binsTime = LocalTime.of(20, 0))
        assertThat(due(days(thursdays), "2026-11-18T19:00:00", later)).isNull()
        assertThat(due(days(thursdays), "2026-11-18T20:00:00", later)).isNotNull()
    }

    @Test
    fun aCollectionThatVanishedBeforeItStartedHasNoReminder() {
        assertThat(due(days(), "2026-11-18T19:00:00")).isNull()
    }

    @Test
    fun aKeptCollectionStaysUntilNoonThoughTheCalendarDroppedIt() {
        val kept = ReminderState(binsKept = thursdays)
        val (due, collection) = due(days(BinCollection(friday, listOf(food))), "2026-11-19T08:00:00", kept)!!
        assertThat(due.key).isEqualTo(DueKey.Bins(thursday))
        assertThat(collection).isEqualTo(thursdays)
        assertThat(due(days(BinCollection(friday, listOf(food))), "2026-11-19T12:00:00", kept)).isNull()
    }

    @Test
    fun theCalendarsBinsWinOverTheKeptOnes() {
        val changed = BinCollection(thursday, listOf(recycling))
        val (_, collection) = due(days(changed), "2026-11-19T08:00:00", ReminderState(binsKept = thursdays))!!
        assertThat(collection).isEqualTo(changed)
    }

    @Test
    fun aKeptCollectionMarkedDoneIsOver() {
        val state = ReminderState(binsKept = thursdays, binsDoneFor = thursday)
        assertThat(due(days(), "2026-11-19T08:00:00", state)).isNull()
    }

    @Test
    fun aKeptCollectionWithNoBinsConnectionIsNothing() {
        val kept = ReminderState(binsKept = thursdays)
        assertThat(due(days(connected = false), "2026-11-19T08:00:00", kept)).isNull()
        assertThat(due(null, "2026-11-19T08:00:00", kept)).isNull()
    }

    @Test
    fun noStoredCollectionsNothingFires() {
        assertThat(due(days(readFailed = true), "2026-11-18T20:00:00")).isNull()
    }

    @Test
    fun theTimeMustFallFromMiddayToNightsStart() {
        val night = LocalTime.of(22, 0)
        assertThat(binsTimeAllowed(LocalTime.of(12, 0), night)).isTrue()
        assertThat(binsTimeAllowed(LocalTime.of(21, 30), night)).isTrue()
        assertThat(binsTimeAllowed(LocalTime.of(11, 30), night)).isFalse()
        assertThat(binsTimeAllowed(LocalTime.of(22, 0), night)).isFalse()
        assertThat(binsTimeAllowed(LocalTime.of(23, 30), null)).isTrue()
        assertThat(PICK_A_BINS_TIME).isEqualTo("Pick a time from midday until night hours start.")
    }
}
