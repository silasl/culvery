package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.HouseBin
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class CalendarBinCollectionsTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val zone = ZoneId.of("Europe/London")
    private val now = MutableStateFlow<LocalDateTime?>(LocalDateTime.of(2026, 3, 12, 9, 0))
    private val rows = MutableStateFlow<List<CalendarRow>>(emptyList())
    private val colours = testBinColours()

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun binCollections() = CalendarBinCollections(now, store::eventsBetween, rows, colours, flowOf(zone))

    private fun day(n: Int) = LocalDate.of(2026, 3, n)

    private fun row(id: String, carriesBins: Boolean = true, health: ConnectionHealth = ConnectionHealth.Ok) =
        CalendarRow(Connection(id, "calendar.test", "Test", emptyMap()), "Test", "event", health, null, carriesBins)

    private suspend fun connect(id: String, visible: Boolean = true) {
        val mapping = mapOf("s-$id" to SourceMapping(PersonId.FAMILY, visible = visible))
        store.addConnection(Connection(id, "calendar.test", "Test", emptyMap()), listOf(CalendarSource("s-$id", "S", writable = false)), mapping)
        rows.value += row(id)
    }

    private suspend fun binDay(id: String, date: LocalDate, kinds: List<String>) {
        val event = RemoteEvent("bins-$id-$date", binsTitle(kinds), EventTime.AllDay(date), EventTime.AllDay(date.plusDays(1)), recurring = false, bins = kinds)
        val range = DateRange(day(1), day(28), zone)
        store.applySync(id, "s-$id", range, SyncResult(listOf(event), emptyList(), SyncCursor("k"), fullReplace = false), day(12))
    }

    private suspend fun Flow<BinDays>.until(predicate: (BinDays) -> Boolean) = withTimeout(10_000) { first(predicate) }

    private fun names(days: BinDays) = days.collections.map { c -> c.date to c.bins.map { it.name } }

    @Test
    fun todayAndTomorrowInDateOrder() = runBlocking<Unit> {
        connect("c1")
        binDay("c1", day(11), listOf("waste"))
        binDay("c1", day(13), listOf("recycling"))
        binDay("c1", day(12), listOf("food"))
        binDay("c1", day(14), listOf("garden"))
        val days = binCollections().days.until { it.collections.size == 2 }
        assertThat(names(days)).containsExactly(day(12) to listOf("Food"), day(13) to listOf("Recycling")).inOrder()
        assertThat(days.connected).isTrue()
        assertThat(days.readFailed).isFalse()
    }

    @Test
    fun theDaysFollowMidnight() = runBlocking<Unit> {
        connect("c1")
        binDay("c1", day(12), listOf("food"))
        binDay("c1", day(14), listOf("garden"))
        val bins = binCollections()
        bins.days.until { names(it) == listOf(day(12) to listOf("Food")) }
        now.value = LocalDateTime.of(2026, 3, 13, 0, 0)
        bins.days.until { names(it) == listOf(day(14) to listOf("Garden")) }
    }

    @Test
    fun twoConnectionsOnOneDateMerge() = runBlocking<Unit> {
        connect("c1")
        connect("c2")
        binDay("c1", day(12), listOf("recycling"))
        binDay("c2", day(12), listOf("food"))
        val days = binCollections().days.until { it.collections.singleOrNull()?.bins?.size == 2 }
        assertThat(names(days)).containsExactly(day(12) to listOf("Food", "Recycling"))
    }

    @Test
    fun aChosenColourFollows() = runBlocking<Unit> {
        connect("c1")
        binDay("c1", day(12), listOf("food", "council-special"))
        val bins = binCollections()
        val first = bins.days.until { it.collections.isNotEmpty() }
        assertThat(first.collections.single().bins).containsExactly(HouseBin("Food", "green"), HouseBin("council-special", null)).inOrder()
        colours.set("food", "purple")
        val after = bins.days.until { it.collections.single().bins.first().inkId == "purple" }
        assertThat(after.collections).containsExactly(BinCollection(day(12), listOf(HouseBin("Food", "purple"), HouseBin("council-special", null))))
    }

    @Test
    fun aHiddenBinsCalendarGivesNoCollectionsButStaysConnected() = runBlocking<Unit> {
        connect("c1", visible = false)
        binDay("c1", day(12), listOf("food"))
        val days = binCollections().days.first()
        assertThat(days).isEqualTo(BinDays(connected = true, readFailed = false, collections = emptyList()))
    }

    @Test
    fun aFailedReadSaysSoAndKeepsTheStoredDates() = runBlocking<Unit> {
        connect("c1")
        binDay("c1", day(12), listOf("food"))
        rows.value = listOf(row("c1", health = ConnectionHealth.Unreachable))
        val days = binCollections().days.until { it.collections.isNotEmpty() }
        assertThat(days.readFailed).isTrue()
        assertThat(days.connected).isTrue()
        assertThat(names(days)).containsExactly(day(12) to listOf("Food"))
    }

    @Test
    fun noBinsConnectionIsNotConnected() = runBlocking<Unit> {
        rows.value = listOf(row("c1", carriesBins = false, health = ConnectionHealth.Unreachable))
        assertThat(binCollections().days.first()).isEqualTo(BinDays(connected = false, readFailed = false, collections = emptyList()))
    }
}
