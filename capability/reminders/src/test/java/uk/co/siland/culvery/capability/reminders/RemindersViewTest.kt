package uk.co.siland.culvery.capability.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.house.BinCollection
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
    private lateinit var alex: Person
    private lateinit var sam: Person
    private lateinit var mia: Person
    private val ticks = MutableStateFlow(0L)
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
        val engine = RemindersEngine(
            store, FakeAlertHost(), clock.minutes, wall, { zone }, MutableStateFlow(true), MutableStateFlow(false), NightHours { false },
            MutableStateFlow(true), null, { PadStart.STARTED }, scope,
        ) {}
        view = RemindersView(store, engine, household, clock, HouseholdZone(household))
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
}
