package uk.co.siland.culvery.capability.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.house.HouseBin
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.AlertHandle
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.test.FakeAlertHost
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.sound.REMINDER_CHIME_MS

@RunWith(AndroidJUnit4::class)
class RemindersEngineTest {
    private val zone = ZoneId.of("Europe/London")
    private val alex = PersonId("alex")
    private lateinit var db: RemindersDatabase
    private lateinit var store: RemindersStore
    private val rigs = mutableListOf<Rig>()
    private var nowMillis = at("2026-11-18T07:00:00")

    @Before
    fun setUp() {
        ShadowLog.clear()
        // Direct executors run every query and transaction on the test's thread, so a pass has finished when the call that caused it returns.
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RemindersDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        store = RemindersStore(db)
    }

    @After
    fun tearDown() {
        rigs.forEach { it.stop() }
        db.close()
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun once(text: String, words: String = "PE kit") =
        LocalDateTime.parse(text).let { ReminderDraft(words, listOf(alex), it.toLocalTime(), Schedule.Once(it.toLocalDate())) }

    private suspend fun add(text: String, words: String = "PE kit") = store.add(once(text, words), alex, nowMillis)!!

    private fun logs() = ShadowLog.getLogsForTag("Reminders").map { it.msg }

    private class FakeDoor(var quiet: Boolean = false, override val volume: Float = 0.6f) : DoorQuiet {
        override val ringShowing = flowOf(false)

        override suspend fun quietNow() = quiet
    }

    /** Runs [onDuck] inside the duck, where a ring can land. */
    private class DuckingHost(private val inner: FakeAlertHost, private val onDuck: () -> Unit) : AlertHost by inner {
        override suspend fun duck(forMillis: Long) {
            inner.duck(forMillis)
            onDuck()
        }
    }

    /** One engine, the way the wall would have it, with every input a test moves. */
    private inner class Rig(door: FakeDoor? = FakeDoor(), lateSetup: Boolean = false, binsFlow: Flow<BinDays?>? = null) {
        val fake = FakeAlertHost()
        val setup = MutableStateFlow(true)
        val setupArrives = CompletableDeferred<Unit>()
        val holiday = MutableStateFlow<Boolean?>(false)
        val lit = MutableStateFlow(true)
        val bins = MutableStateFlow<BinDays?>(null)
        var night: Boolean? = false
        val minutes = MutableStateFlow(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone))
        val chimes = mutableListOf<Float>()
        var chimeResult = PadStart.STARTED
        var chimeThrows = 0
        var chimeError: Error? = null
        var onDuck: (() -> Unit)? = null
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = RemindersEngine(
            store, DuckingHost(fake) { onDuck?.invoke() }, minutes, { nowMillis }, { zone }, if (lateSetup) flow { setupArrives.await(); emitAll(setup) } else setup, holiday, NightHours { night }, binsFlow ?: bins, lit, door,
            { volume ->
                chimes += volume
                chimeError?.let { chimeError = null; throw it }
                if (chimeThrows > 0) {
                    chimeThrows--
                    error("chime")
                }
                chimeResult
            },
            scope,
        ) {}

        init {
            rigs += this
        }

        fun start() = engine.start()

        fun tick(text: String) {
            nowMillis = at(text)
            minutes.value = LocalDateTime.parse(text)
        }

        fun stop() = scope.cancel()

        val key get() = engine.showing.value?.due?.key
    }

    private fun mine(id: Long) = DueKey.Mine(id)

    @Test
    fun byDayOnALitWallTheCardShowsAndChimes() = runTest {
        val rig = Rig().also { it.start() }
        val id = add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isEqualTo(Showing(rig.engine.waiting.value.single(), 1))
        assertThat(rig.key).isEqualTo(mine(id))
        assertThat(rig.chimes).containsExactly(0.6f)
        assertThat(store.reminders.first().single().chimedAtMillis).isEqualTo(at("2026-11-18T07:30:00"))
    }

    @Test
    fun byDayOnADarkWallItWakesThenChimesOnceLit() = runTest {
        val rig = Rig().also { it.lit.value = false; it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.fake.wakes).isEqualTo(1)
        assertThat(rig.chimes).isEmpty()
        rig.lit.value = true
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun leftAloneTheWallGoesDarkOverItSilently() = runTest {
        val rig = Rig().also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        rig.lit.value = false
        rig.tick("2026-11-18T07:45:00")
        assertThat(rig.fake.showing.value).isTrue()
        assertThat(rig.engine.showing.value).isNotNull()
        rig.lit.value = true
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aNewerTimeOnAWaitingReminderWakesAndChimesAgain() = runTest {
        val rig = Rig().also { it.start() }
        store.add(ReminderDraft("Swimming bag", listOf(alex), LocalTime.of(7, 30), Schedule.Weekly(DayOfWeek.entries.toSet())), alex, nowMillis)
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.chimes).hasSize(1)
        val wakes = rig.fake.wakes
        rig.tick("2026-11-19T07:30:00")
        assertThat(rig.chimes).hasSize(2)
        assertThat(rig.fake.wakes).isGreaterThan(wakes)
        assertThat(rig.engine.showing.value!!.due.dueAtMillis).isEqualTo(at("2026-11-19T07:30:00"))
        assertThat(rig.engine.showing.value!!.of).isEqualTo(1)
    }

    @Test
    fun inDoorQuietHoursItShowsAndOwesUntilTheyEnd() = runTest {
        val door = FakeDoor(quiet = true)
        val rig = Rig(door).also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNotNull()
        rig.tick("2026-11-18T07:31:00")
        assertThat(rig.chimes).isEmpty()
        door.quiet = false
        rig.tick("2026-11-18T07:32:00")
        assertThat(rig.chimes).containsExactly(0.6f)
    }

    @Test
    fun quietHoursEndingOnADarkWallWakeItAndTheChimePlaysOnceLit() = runTest {
        val door = FakeDoor(quiet = true)
        val rig = Rig(door).also { it.lit.value = false; it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNotNull()
        val wakes = rig.fake.wakes
        door.quiet = false
        rig.tick("2026-11-18T07:31:00")
        assertThat(rig.fake.wakes).isGreaterThan(wakes)
        assertThat(rig.chimes).isEmpty()
        rig.lit.value = true
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aFailingInputDoesNotStopTheOthers() = runTest {
        val rig = Rig(binsFlow = flow { error("the calendar went away") }).also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(logs()).contains("An input failed (IllegalStateException)")
        assertThat(logs().any { it.contains("went away") }).isFalse()
    }

    @Test
    fun atNightOnTheClockItStaysDown() = runTest {
        nowMillis = at("2026-11-18T06:00:00")
        val rig = Rig().also { it.night = true; it.lit.value = false; it.start() }
        add("2026-11-18T06:45:00")
        rig.tick("2026-11-18T06:45:00")
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.engine.waiting.value).hasSize(1)
        assertThat(rig.fake.shown).isEqualTo(0)
        assertThat(rig.chimes).isEmpty()
        assertThat(logs()).contains("held night")
    }

    @Test
    fun atNightATapShowsItSilentlyThenItComesDown() = runTest {
        nowMillis = at("2026-11-18T06:00:00")
        val rig = Rig().also { it.night = true; it.lit.value = false; it.start() }
        add("2026-11-18T06:45:00")
        rig.tick("2026-11-18T06:45:00")
        rig.lit.value = true
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).isEmpty()
        rig.lit.value = false
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.fake.showing.value).isFalse()
        assertThat(rig.chimes).isEmpty()
    }

    @Test
    fun whenNightEndsItShowsAndChimesEvenIfItChimedTheEveningBefore() = runTest {
        nowMillis = at("2026-11-17T19:00:00")
        val rig = Rig().also { it.start() }
        add("2026-11-17T20:00:00")
        rig.tick("2026-11-17T20:00:00")
        assertThat(rig.chimes).hasSize(1)
        rig.night = true
        rig.lit.value = false
        rig.tick("2026-11-17T21:00:00")
        assertThat(rig.engine.showing.value).isNull()
        rig.tick("2026-11-18T06:00:00")
        assertThat(rig.chimes).hasSize(1)
        val wakes = rig.fake.wakes
        rig.lit.value = true
        rig.night = false
        rig.tick("2026-11-18T07:00:00")
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).hasSize(2)
        assertThat(rig.fake.wakes).isGreaterThan(wakes)
    }

    @Test
    fun onHolidayItStaysDownAndChimesWhenHolidayEnds() = runTest {
        val rig = Rig().also { it.holiday.value = true; it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.chimes).isEmpty()
        assertThat(logs()).contains("held holiday")
        rig.holiday.value = false
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aDoorCardTakesTheLayerAndTheReminderReturns() = runTest {
        val rig = Rig().also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        val ring = rig.fake.show(loud = true) {}
        assertThat(rig.engine.showing.value).isNull()
        assertThat(logs()).contains("held ring")
        ring.dismiss()
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aChimeRefusedForARingIsStillOwed() = runTest {
        val rig = Rig().also { it.chimeResult = PadStart.DOORBELL; it.start() }
        val id = add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.chimes).isNotEmpty()
        assertThat(store.reminders.first().single { it.id == id }.chimedAtMillis).isNull()
        rig.chimeResult = PadStart.STARTED
        val refused = rig.chimes.size
        rig.tick("2026-11-18T07:31:00")
        assertThat(rig.chimes).hasSize(refused + 1)
        assertThat(store.reminders.first().single { it.id == id }.chimedAtMillis).isEqualTo(at("2026-11-18T07:31:00"))
        rig.tick("2026-11-18T07:32:00")
        assertThat(rig.chimes).hasSize(refused + 1)
    }

    @Test
    fun awayOrInSetupItStaysDown() = runTest {
        val rig = Rig().also { it.setup.value = false; it.fake.inFront.value = false; it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNull()
        assertThat(logs()).contains("held away")
        rig.setup.value = true
        assertThat(rig.engine.showing.value).isNull()
        rig.fake.inFront.value = true
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aSetupValueThatArrivesLateLogsNoHeldAway() = runTest {
        val rig = Rig(lateSetup = true).also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(logs()).doesNotContain("held away")
        assertThat(rig.engine.showing.value).isNull()
        rig.setupArrives.complete(Unit)
        assertThat(rig.engine.showing.value).isNotNull()
    }

    @Test
    fun theChimeIsPaidOnceAcrossARestart() = runTest {
        val first = Rig().also { it.start() }
        val id = add("2026-11-18T07:30:00")
        first.tick("2026-11-18T07:30:00")
        assertThat(first.chimes).hasSize(1)
        first.stop()
        val second = Rig().also { it.start() }
        assertThat(second.key).isEqualTo(mine(id))
        assertThat(second.chimes).isEmpty()
        second.tick("2026-11-18T07:31:00")
        assertThat(second.chimes).isEmpty()
    }

    @Test
    fun nightEndingWhileTheWallWasOffStillOwesTheChime() = runTest {
        nowMillis = at("2026-11-17T19:00:00")
        val first = Rig().also { it.start() }
        val id = add("2026-11-17T20:00:00")
        first.tick("2026-11-17T20:00:00")
        first.night = true
        first.lit.value = false
        first.tick("2026-11-17T21:00:00")
        assertThat(first.chimes).hasSize(1)
        first.stop()
        nowMillis = at("2026-11-18T07:00:00")
        val second = Rig().also { it.start() }
        assertThat(second.key).isEqualTo(mine(id))
        assertThat(second.chimes).hasSize(1)
    }

    @Test
    fun aRingDuringTheDuckLeavesTheChimeOwed() = runTest {
        val rig = Rig().also { it.start() }
        var ring: AlertHandle? = null
        rig.onDuck = {
            rig.onDuck = null
            ring = rig.fake.show(loud = true) {}
        }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(ring).isNotNull()
        assertThat(rig.chimes).isEmpty()
        assertThat(rig.engine.showing.value).isNull()
        ring!!.dismiss()
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aChimeWhoseMarkCannotBeSavedPlaysOnce() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER no_marks BEFORE UPDATE OF chimedAtMillis ON reminder BEGIN SELECT RAISE(ABORT, 'no'); END",
        )
        val rig = Rig().also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.chimes).hasSize(1)
        assertThat(logs().filter { it.startsWith("A pass failed") }).isNotEmpty()
        repeat(10) { rig.tick("2026-11-18T07:${31 + it}:00") }
        assertThat(rig.chimes).hasSize(1)
        assertThat(rig.engine.showing.value).isNotNull()
    }

    @Test
    fun aTimeMissedWhileOffIsWaitingAtStart() = runTest {
        val id = add("2026-11-18T07:30:00")
        nowMillis = at("2026-11-18T09:00:00")
        val rig = Rig().also { it.start() }
        assertThat(rig.key).isEqualTo(mine(id))
        assertThat(rig.engine.showing.value!!.due.dueAtMillis).isEqualTo(at("2026-11-18T07:30:00"))
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun severalOwedShareOneChime() = runTest {
        val rig = Rig().also { it.start() }
        val a = add("2026-11-18T07:30:00")
        val b = add("2026-11-18T07:30:00", "Swimming bag")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value!!.of).isEqualTo(2)
        assertThat(rig.key).isEqualTo(mine(a))
        assertThat(rig.chimes).hasSize(1)
        assertThat(store.reminders.first().map { it.id to it.chimedAtMillis })
            .containsExactly(a to at("2026-11-18T07:30:00"), b to at("2026-11-18T07:30:00"))
    }

    @Test
    fun musicDucksForTheChimesLengthOnly() = runTest {
        val rig = Rig().also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.fake.ducks).containsExactly(REMINDER_CHIME_MS)
        assertThat(rig.fake.loud).isNotEqualTo(true)
    }

    @Test
    fun theCardNeverHoldsTheWall() = runTest {
        val rig = Rig().also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.fake.showing.value).isTrue()
        assertThat(rig.fake.holdsAwake).isFalse()
    }

    @Test
    fun withNoDoorTheChimePlaysAtFullVolume() = runTest {
        val rig = Rig(door = null).also { it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.chimes).containsExactly(1f)
    }

    @Test
    fun doneMovesToTheNextAndLaterBringsItBackWithItsChime() = runTest {
        val rig = Rig().also { it.start() }
        val a = add("2026-11-18T07:30:00")
        val b = add("2026-11-18T07:30:00", "Swimming bag")
        rig.tick("2026-11-18T07:30:00")
        rig.engine.done(mine(a))
        assertThat(rig.key).isEqualTo(mine(b))
        assertThat(rig.engine.showing.value!!.of).isEqualTo(1)
        assertThat(store.reminders.first().map { it.id }).containsExactly(b)
        assertThat(rig.chimes).hasSize(1)
        rig.engine.later(mine(b))
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.engine.waiting.value.map { it.key }).containsExactly(mine(b))
        rig.tick("2026-11-18T07:59:00")
        assertThat(rig.engine.showing.value).isNull()
        rig.tick("2026-11-18T08:00:00")
        assertThat(rig.key).isEqualTo(mine(b))
        assertThat(rig.chimes).hasSize(2)
    }

    @Test
    fun doneOnAReminderThatHasGoneIsNothing() = runTest {
        val rig = Rig().also { it.start() }
        val id = add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        rig.engine.done(mine(id + 100))
        rig.engine.later(mine(id + 100))
        assertThat(store.reminders.first().map { it.id }).containsExactly(id)
        assertThat(rig.key).isEqualTo(mine(id))
        store.delete(id)
        rig.tick("2026-11-18T07:31:00")
        rig.engine.done(mine(id))
        assertThat(rig.engine.showing.value).isNull()
        assertThat(logs()).doesNotContain("done")
        assertThat(logs()).doesNotContain("later")
    }

    @Test
    fun aSecondDoneForTheSameKeyLeavesTheNextCard() = runTest {
        val rig = Rig().also { it.start() }
        val a = add("2026-11-18T07:30:00")
        val b = add("2026-11-18T07:30:00", "Swimming bag")
        rig.tick("2026-11-18T07:30:00")
        rig.engine.done(mine(a))
        rig.engine.done(mine(a))
        assertThat(rig.key).isEqualTo(mine(b))
        assertThat(store.reminders.first().map { it.id }).containsExactly(b)
        assertThat(logs().count { it == "done" }).isEqualTo(1)
    }

    @Test
    fun aChimeThatCannotPlayIsNotTriedAgain() = runTest {
        val rig = Rig().also { it.chimeResult = PadStart.FAILED; it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        repeat(10) { rig.tick("2026-11-18T07:${31 + it}:00") }
        assertThat(rig.chimes).hasSize(1)
        assertThat(logs()).doesNotContain("chime")
    }

    @Test
    fun aPassThatThrowsLeavesTheEngineRunning() = runTest {
        val rig = Rig().also { it.start() }
        rig.chimeThrows = 1
        val first = add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(logs()).contains("A pass failed (IllegalStateException)")
        val second = add("2026-11-18T07:31:00", "Swimming bag")
        rig.tick("2026-11-18T07:31:00")
        assertThat(rig.engine.waiting.value.map { it.key }).containsExactly(mine(first), mine(second))
        assertThat(rig.engine.showing.value!!.of).isEqualTo(2)
        assertThat(store.reminders.first().map { it.chimedAtMillis }).containsExactly(at("2026-11-18T07:30:00"), at("2026-11-18T07:31:00"))
    }

    @Test
    fun nightEndingOnADarkWallWakesItAndChimesOnceLit() = runTest {
        nowMillis = at("2026-11-18T06:00:00")
        val rig = Rig().also { it.night = true; it.lit.value = false; it.start() }
        add("2026-11-18T06:45:00")
        rig.tick("2026-11-18T06:45:00")
        assertThat(rig.engine.showing.value).isNull()
        val wakes = rig.fake.wakes
        rig.night = false
        rig.tick("2026-11-18T07:00:00")
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.fake.wakes).isGreaterThan(wakes)
        assertThat(rig.chimes).isEmpty()
        rig.lit.value = true
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun holidayEndingOnADarkWallWakesItAndChimesOnceLit() = runTest {
        val rig = Rig().also { it.holiday.value = true; it.lit.value = false; it.start() }
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(rig.engine.showing.value).isNull()
        val wakes = rig.fake.wakes
        rig.holiday.value = false
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.fake.wakes).isGreaterThan(wakes)
        assertThat(rig.chimes).isEmpty()
        rig.lit.value = true
        assertThat(rig.chimes).hasSize(1)
    }

    @Test
    fun aTapAtNightWithANewlyOwedTimeDoesNotWake() = runTest {
        nowMillis = at("2026-11-18T06:00:00")
        val rig = Rig().also { it.night = true; it.lit.value = true; it.start() }
        add("2026-11-18T06:45:00")
        rig.tick("2026-11-18T06:45:00")
        assertThat(rig.engine.showing.value).isNotNull()
        assertThat(rig.fake.wakes).isEqualTo(0)
        assertThat(rig.chimes).isEmpty()
    }

    @Test
    fun anErrorInAPassIsLoggedAndTheEngineKeepsRunning() = runTest {
        val rig = Rig().also { it.start() }
        rig.chimeError = AssertionError("chime")
        add("2026-11-18T07:30:00")
        rig.tick("2026-11-18T07:30:00")
        assertThat(logs()).contains("A pass failed (AssertionError)")
        val second = add("2026-11-18T07:31:00", "Swimming bag")
        rig.tick("2026-11-18T07:31:00")
        assertThat(rig.engine.waiting.value.map { it.key }).contains(mine(second))
        assertThat(rig.chimes).isNotEmpty()
        assertThat(store.reminders.first().map { it.chimedAtMillis }).doesNotContain(null)
    }

    @Test
    fun theLogsCarryCountsAndStatesOnly() = runTest {
        val rig = Rig().also { it.start() }
        val a = add("2026-11-18T07:30:00")
        add("2026-11-18T07:30:00", "Swimming bag")
        rig.tick("2026-11-18T07:30:00")
        rig.engine.done(mine(a))
        assertThat(logs()).containsExactly("due 2", "card up 1 of 2", "chime", "done", "card up 1 of 1").inOrder()
    }

    private val thursday = LocalDate.of(2026, 11, 19)
    private val thursdays = BinCollection(thursday, listOf(HouseBin("Food", "green"), HouseBin("Recycling", null)))
    private val binsKey = DueKey.Bins(thursday)

    private fun binDays(vararg collections: BinCollection, readFailed: Boolean = false) = BinDays(true, readFailed, collections.toList())

    @Test
    fun binsShowAndChimeLikeAnyReminder() = runTest {
        val rig = Rig().also { it.bins.value = binDays(thursdays); it.start() }
        rig.tick("2026-11-18T17:59:00")
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.engine.waiting.value).isEmpty()
        rig.tick("2026-11-18T18:00:00")
        assertThat(rig.key).isEqualTo(binsKey)
        assertThat(rig.chimes).containsExactly(0.6f)
        assertThat(store.state.first().binsChimedAtMillis).isEqualTo(at("2026-11-18T18:00:00"))
        assertThat(store.state.first().binsKept).isEqualTo(thursdays)
        rig.tick("2026-11-18T18:01:00")
        assertThat(rig.chimes).hasSize(1)
        assertThat(logs()).containsExactly("due 1", "bins due", "card up 1 of 1", "chime").inOrder()
    }

    @Test
    fun laterAndDoneWorkOnTheBinsCard() = runTest {
        val rig = Rig().also { it.bins.value = binDays(thursdays); it.start() }
        rig.tick("2026-11-18T18:00:00")
        rig.engine.later(binsKey)
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.engine.waiting.value.map { it.key }).containsExactly(binsKey)
        rig.tick("2026-11-18T18:29:00")
        assertThat(rig.engine.showing.value).isNull()
        rig.tick("2026-11-18T18:30:00")
        assertThat(rig.key).isEqualTo(binsKey)
        assertThat(rig.chimes).hasSize(2)
        rig.engine.done(binsKey)
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.engine.waiting.value).isEmpty()
        assertThat(store.state.first().binsDoneFor).isEqualTo(thursday)
        assertThat(store.state.first().binsKept).isNull()
        assertThat(logs()).containsAtLeast("later", "done")
        assertThat(logs()).doesNotContain("bins cleared")
    }

    @Test
    fun binsClearAtNoonEvenOnHoliday() = runTest {
        val rig = Rig().also { it.bins.value = binDays(thursdays); it.holiday.value = true; it.start() }
        rig.tick("2026-11-18T18:00:00")
        assertThat(rig.engine.showing.value).isNull()
        assertThat(rig.engine.waiting.value.map { it.key }).containsExactly(binsKey)
        rig.tick("2026-11-19T11:59:00")
        assertThat(rig.engine.waiting.value).hasSize(1)
        rig.tick("2026-11-19T12:00:00")
        assertThat(rig.engine.waiting.value).isEmpty()
        assertThat(logs().filter { it == "bins cleared" }).hasSize(1)
        assertThat(store.state.first().binsKept).isNull()
    }

    @Test
    fun aFailingBinsWriteStillLetsAFamilyReminderShow() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER no_kept BEFORE INSERT ON reminder_state WHEN NEW.binsKeptDate IS NOT NULL BEGIN SELECT RAISE(ABORT, 'no'); END",
        )
        val rig = Rig().also { it.bins.value = binDays(thursdays); it.start() }
        val id = add("2026-11-18T18:00:00")
        rig.tick("2026-11-18T18:00:00")
        assertThat(rig.engine.waiting.value.map { it.key }).contains(mine(id))
        assertThat(logs()).contains("A bins write failed (SQLiteConstraintException)")
    }

    @Test
    fun aFailedReadStillFiresFromStoredDates() = runTest {
        val rig = Rig().also { it.bins.value = binDays(thursdays, readFailed = true); it.start() }
        rig.tick("2026-11-18T18:00:00")
        assertThat(rig.key).isEqualTo(binsKey)
    }

    @Test
    fun binsAndAReminderShareTheCardOldestFirst() = runTest {
        val rig = Rig().also { it.bins.value = binDays(thursdays); it.start() }
        val id = add("2026-11-18T17:30:00")
        rig.tick("2026-11-18T17:30:00")
        assertThat(rig.key).isEqualTo(mine(id))
        rig.tick("2026-11-18T18:00:00")
        assertThat(rig.key).isEqualTo(mine(id))
        assertThat(rig.engine.showing.value!!.of).isEqualTo(2)
        rig.engine.done(mine(id))
        assertThat(rig.key).isEqualTo(binsKey)
        assertThat(rig.engine.showing.value!!.of).isEqualTo(1)
    }

    @Test
    fun aBinsReminderOutlivesTheMorningsRead() = runTest {
        val first = Rig().also { it.bins.value = binDays(thursdays); it.start() }
        first.tick("2026-11-18T18:00:00")
        assertThat(first.chimes).hasSize(1)
        first.bins.value = binDays(BinCollection(LocalDate.of(2026, 11, 20), listOf(HouseBin("Garden", null))))
        first.tick("2026-11-19T00:30:00")
        assertThat(first.key).isEqualTo(binsKey)
        first.stop()

        nowMillis = at("2026-11-19T07:00:00")
        val second = Rig().also { it.bins.value = binDays(BinCollection(LocalDate.of(2026, 11, 20), listOf(HouseBin("Garden", null)))); it.start() }
        val due = second.engine.showing.value!!.due
        assertThat(due.key).isEqualTo(binsKey)
        val card = binsCard(binsKey, store.state.first().binsKept, due.dueAtMillis, nowMillis, zone, 1)!!
        assertThat(card.words).isEqualTo("Bins out today")
        assertThat(card.detail).isEqualTo("Food, Recycling")
        assertThat(second.chimes).isEmpty()
        second.tick("2026-11-19T12:00:00")
        assertThat(second.engine.showing.value).isNull()
        assertThat(store.state.first().binsKept).isNull()
        assertThat(logs()).contains("bins cleared")
    }
}
