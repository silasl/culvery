package uk.co.siland.culvery.capability.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinCollections
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.HouseBin
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.FakeAlertHost
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.ui.PersonPalette

@RunWith(AndroidJUnit4::class)
class RemindersViewTest {
    private val zone = ZoneId.of("Europe/London")
    private lateinit var db: RemindersDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: RemindersStore
    private lateinit var household: HouseholdRepository
    private lateinit var view: RemindersView
    private lateinit var engine: RemindersEngine
    private lateinit var alex: Person
    private lateinit var sam: Person
    private lateinit var mia: Person
    private val ticks = MutableStateFlow(0L)
    private val binDays = MutableStateFlow(BinDays(connected = false, readFailed = false, collections = emptyList()))
    private var nowMillis = at("2026-11-18T07:00:00")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Direct executors run every query on the test's thread, so a pass has finished when the call that caused it returns.
        db = Room.inMemoryDatabaseBuilder(context, RemindersDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        store = RemindersStore(db)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("Home", 51.5, -0.1, zone.id))
        alex = household.addPerson("Alex", PersonPalette.colors[0], Role.ADMIN)
        sam = household.addPerson("Sam", PersonPalette.colors[1], Role.ADULT)
        mia = household.addPerson("Mia", PersonPalette.colors[2], Role.CHILD)
        val wall = WallClock { nowMillis }
        val clock = HouseholdClock(HouseholdZone(household).zone, wall, scope, ticks)
        ticks.value = nowMillis
        engine = RemindersEngine(
            store, FakeAlertHost(), clock.minutes, wall, { zone }, MutableStateFlow(true), MutableStateFlow(false), NightHours { false },
            binDays, MutableStateFlow(true), null, { PadStart.STARTED }, scope,
        ) {}
        view = RemindersView(store, engine, household, clock, HouseholdZone(household), Optional.of(object : BinCollections { override val days: Flow<BinDays> = binDays }), scope)
        engine.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        householdDb.close()
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun tick(text: String) {
        nowMillis = at(text)
        ticks.value = nowMillis
    }

    private suspend fun add(text: String, words: String, who: List<PersonId>) =
        store.add(
            LocalDateTime.parse(text).let { ReminderDraft(words, who, it.toLocalTime(), Schedule.Once(it.toLocalDate())) },
            alex.id, nowMillis,
        )!!

    @Test
    fun theCardCarriesWhoAndTheDueTime() = runTest {
        val id = add("2026-11-18T07:30:00", "PE kit", listOf(mia.id, sam.id))
        tick("2026-11-18T07:30:00")
        val card = view.card.first { it != null }
        assertThat(card).isEqualTo(
            CardUi(DueKey.Mine(id), "PE kit", listOf(StripeUi(colour = sam.color), StripeUi(colour = mia.color)), "Sam and Mia", "07:30"),
        )
    }

    @Test
    fun aReminderForFamilyReadsFamily() = runTest {
        add("2026-11-18T07:30:00", "Tidy up", listOf(PersonId.FAMILY))
        tick("2026-11-18T07:30:00")
        val card = view.card.first { it != null }!!
        assertThat(card.who).isEqualTo("Family")
        assertThat(card.stripes).containsExactly(StripeUi(colour = Person.Family.color))
    }

    @Test
    fun theCountShowsOnlyWhenSeveralWait() = runTest {
        val first = add("2026-11-18T07:30:00", "PE kit", listOf(mia.id))
        tick("2026-11-18T07:30:00")
        assertThat(view.card.first { it != null }!!.of).isNull()
        add("2026-11-18T07:45:00", "Swimming bag", listOf(mia.id))
        tick("2026-11-18T07:45:00")
        val card = view.card.first { it?.of != null }!!
        assertThat(card.of).isEqualTo(2)
        assertThat(card.key).isEqualTo(DueKey.Mine(first))
    }

    @Test
    fun theDueTimeGainsItsDayAfterMidnight() = runTest {
        add("2026-11-18T23:30:00", "Phone charger", listOf(mia.id))
        tick("2026-11-18T23:30:00")
        assertThat(view.card.first { it != null }!!.dueAt).isEqualTo("23:30")
        tick("2026-11-19T00:01:00")
        assertThat(view.card.first { it?.dueAt != "23:30" }!!.dueAt).isEqualTo("Wed 23:30")
    }

    @Test
    fun theCardHidesItsWords() {
        val card = CardUi(DueKey.Mine(4), "PE kit", listOf(StripeUi(colour = mia.color)), "Mia", "07:30", detail = "Food", of = 2)
        assertThat(card.toString()).isEqualTo("CardUi(${DueKey.Mine(4)})")
    }

    @Test
    fun dueTextIsTheTimeTodayAndTheDayAfter() {
        val due = at("2026-11-18T16:30:00")
        assertThat(dueText(due, at("2026-11-18T23:59:00"), zone)).isEqualTo("16:30")
        assertThat(dueText(due, at("2026-11-19T00:00:00"), zone)).isEqualTo("Wed 16:30")
    }

    @Test
    fun aBinsCardNamesTheBinsAndReadsTonightUntilMidnight() {
        val kept = BinCollection(LocalDate.of(2026, 11, 19), listOf(HouseBin("Food", "green"), HouseBin("Recycling", null)))
        val key = DueKey.Bins(kept.date)
        val due = at("2026-11-18T18:00:00")
        val tonight = binsCard(key, kept, due, at("2026-11-18T19:00:00"), zone, 1)
        assertThat(tonight).isEqualTo(
            CardUi(key, "Bins out tonight", listOf(StripeUi(inkId = "green"), StripeUi()), "Bins", "18:00", detail = "Food, Recycling"),
        )
        assertThat(binsCard(key, kept, due, at("2026-11-19T07:00:00"), zone, 2)).isEqualTo(tonight!!.copy(words = "Bins out today", dueAt = "Wed 18:00", of = 2))
        assertThat(binsCard(key, null, due, due, zone, 1)).isNull()
    }

    private suspend fun addWeekly(time: String, words: String, who: List<PersonId>, vararg days: DayOfWeek) =
        store.add(ReminderDraft(words, who, java.time.LocalTime.parse(time), Schedule.Weekly(days.toSet())), alex.id, nowMillis)!!

    @Test
    fun waitingRowsShowTheDueTimeOrBackAt() = runTest {
        val id = add("2026-11-18T07:30:00", "PE kit", listOf(mia.id))
        tick("2026-11-18T07:30:00")
        val row = view.tab.first { it.waiting.isNotEmpty() }.waiting.single()
        assertThat(row).isEqualTo(RowUi(DueKey.Mine(id), id, listOf(StripeUi(colour = mia.color)), "07:30", "PE kit", "Mia", null))
        engine.later(DueKey.Mine(id))
        assertThat(view.tab.first { it.waiting.singleOrNull()?.note != null }.waiting.single().note).isEqualTo("Back at 08:00")
    }

    @Test
    fun todayHoldsNextTimesToday() = runTest {
        add("2026-11-18T16:30:00", "Swimming bag", listOf(mia.id))
        addWeekly("12:00", "Lunch money", listOf(sam.id), *DayOfWeek.entries.toTypedArray())
        add("2026-11-19T09:00:00", "Dentist", listOf(sam.id))
        val tab = view.tab.first { it.today.size == 2 }
        assertThat(tab.today.map { it.words }).containsExactly("Lunch money", "Swimming bag").inOrder()
        assertThat(tab.today.map { it.time }).containsExactly("12:00", "16:30").inOrder()
        assertThat(tab.today.map { it.key }).containsExactly(null, null)
        assertThat(tab.comingUp.map { it.words }).containsExactly("Dentist")
    }

    @Test
    fun comingUpIsByNextTimeWithItsRepeatWords() = runTest {
        val friday = addWeekly("08:00", "Library books", listOf(mia.id), DayOfWeek.FRIDAY)
        val thursday = add("2026-11-19T09:00:00", "Dentist", listOf(sam.id))
        val rows = view.tab.first { it.comingUp.size == 2 }.comingUp
        assertThat(rows.map { it.id }).containsExactly(thursday, friday).inOrder()
        assertThat(rows.map { it.time }).containsExactly("Thu 09:00", "Fri 08:00").inOrder()
        assertThat(rows.map { it.note }).containsExactly("Once", "Every Friday").inOrder()
    }

    @Test
    fun aWaitingRepeatIsListedOnce() = runTest {
        addWeekly("07:30", "Vitamins", listOf(mia.id), *DayOfWeek.entries.toTypedArray())
        tick("2026-11-18T07:30:00")
        val tab = view.tab.first { it.waiting.isNotEmpty() }
        assertThat(tab.waiting).hasSize(1)
        assertThat(tab.today).isEmpty()
        assertThat(tab.comingUp).isEmpty()
    }

    @Test
    fun theCardAndTodayFollowMidnight() = runTest {
        val id = add("2026-11-19T09:00:00", "Dentist", listOf(sam.id))
        assertThat(view.tab.first().comingUp.map { it.id }).containsExactly(id)
        tick("2026-11-19T00:01:00")
        val tab = view.tab.first { it.today.isNotEmpty() }
        assertThat(tab.today.map { it.id }).containsExactly(id)
        assertThat(tab.today.single().time).isEqualTo("09:00")
        assertThat(tab.comingUp).isEmpty()
    }

    @Test
    fun aRowHidesItsWords() {
        val row = RowUi(DueKey.Mine(4), 4, listOf(StripeUi(colour = mia.color)), "07:30", "PE kit", "Mia", "Once")
        assertThat(row.toString()).isEqualTo("RowUi(${DueKey.Mine(4)}, 4)")
    }

    @Test
    fun theBinsRowTitleSaysWhenItWillRemind() {
        assertThat(binsRowTitle(java.time.LocalTime.of(18, 0))).isEqualTo("Bins · The evening before a collection, 18:00")
    }

    private val thursday = LocalDate.of(2026, 11, 19)
    private val food = HouseBin("Food", "green")
    private val recycling = HouseBin("Recycling", null)

    @Test
    fun changedBinsFollowOnTheCard() = runTest {
        binDays.value = BinDays(true, false, listOf(BinCollection(thursday, listOf(food))))
        tick("2026-11-18T18:00:00")
        assertThat(view.card.first { it != null }!!.detail).isEqualTo("Food")
        binDays.value = BinDays(true, false, listOf(BinCollection(thursday, listOf(food, recycling))))
        val card = view.card.first { it?.detail == "Food, Recycling" }!!
        assertThat(card.stripes).containsExactly(StripeUi(inkId = "green"), StripeUi()).inOrder()
        assertThat(card.words).isEqualTo("Bins out tonight")
        assertThat(card.who).isEqualTo("Bins")
    }

    @Test
    fun aWaitingBinsReminderIsARowAndCountsInTheBadge() = runTest {
        binDays.value = BinDays(true, false, listOf(BinCollection(thursday, listOf(food, recycling))))
        tick("2026-11-18T18:00:00")
        val row = view.tab.first { it.waiting.isNotEmpty() }.waiting.single()
        assertThat(row).isEqualTo(
            RowUi(DueKey.Bins(thursday), null, listOf(StripeUi(inkId = "green"), StripeUi()), "18:00", "Bins out tonight", "Bins", "Food, Recycling"),
        )
        assertThat(badgeOf(engine.waiting.value.size)).isEqualTo(RailBadge.Count(1, "1 waiting"))
    }

    @Test
    fun aWaitingBinsRowReadsBackAtWhenSnoozed() {
        val kept = BinCollection(thursday, listOf(food))
        val due = Due(DueKey.Bins(thursday), at("2026-11-18T18:00:00"), at("2026-11-18T18:30:00"), null)
        val tab = tabOf(listOf(due), emptyList(), ReminderState(binsKept = kept), emptyList(), at("2026-11-18T18:10:00"), zone, null)
        assertThat(tab.waiting.single().note).isEqualTo("Back at 18:30")
        assertThat(tab.bins).isNull()
        val holdingNothing = tabOf(listOf(due), emptyList(), ReminderState(), emptyList(), at("2026-11-18T18:10:00"), zone, null)
        assertThat(holdingNothing.waiting).isEmpty()
    }

    @Test
    fun theBinsRowShowsOnlyWhileABinsConnectionExists() = runTest {
        assertThat(view.tab.first().bins).isNull()
        binDays.value = BinDays(true, false, emptyList())
        assertThat(view.tab.first { it.bins != null }.bins).isEqualTo(BinsRowUi(on = true, time = java.time.LocalTime.of(18, 0), cantRead = false))
        binDays.value = BinDays(true, true, emptyList())
        assertThat(view.tab.first { it.bins?.cantRead == true }.bins).isNotNull()
        store.setBins(false, java.time.LocalTime.of(19, 30))
        assertThat(view.tab.first { it.bins?.on == false }.bins).isEqualTo(BinsRowUi(on = false, time = java.time.LocalTime.of(19, 30), cantRead = true))
        binDays.value = BinDays(false, false, emptyList())
        assertThat(view.tab.first { it.bins == null }.bins).isNull()
        val clock = HouseholdClock(HouseholdZone(household).zone, WallClock { nowMillis }, scope, ticks)
        val bare = RemindersView(store, engine, household, clock, HouseholdZone(household), Optional.empty(), scope)
        assertThat(bare.tab.first().bins).isNull()
    }
}
