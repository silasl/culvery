package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class CalendarCapabilityTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var capability: CalendarCapability

    @Before
    fun setUp() {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        val zone = HouseholdZone(household)
        capability = CalendarCapability(
            CalendarRepository(store, household, zone, emptySet(), emptySet()), zone, WallClock { 0L }, stubEditor(store, zone), stubConnections(store),
            stubReview(store, household),
        )
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private suspend fun connect() =
        store.addConnection(Connection("c1", "calendar.test", "Test", emptyMap()), emptyList(), emptyMap())

    @Test
    fun railEntryIsCalendarAtOrderTen() {
        assertThat(capability.id).isEqualTo("calendar")
        assertThat(capability.label).isEqualTo("Calendar")
        assertThat(capability.icon).isEqualTo("calendar_month")
        assertThat(capability.order).isEqualTo(10)
    }

    @Test
    fun noConnectionsGivesTheConnectCardAndNoTab() = runTest {
        val cards = capability.cards().first()
        assertThat(cards.map { Triple(it.id, it.size, it.priority) })
            .containsExactly(Triple(CONNECT_CARD_ID, HomeCardSize.TALL, 100))
        assertThat(capability.hasTab.first()).isFalse()
    }

    @Test
    fun aConnectionGivesTodayAndComingUpAndTheTab() = runTest {
        connect()
        val cards = capability.cards().first()
        assertThat(cards.map { Triple(it.id, it.size, it.priority) }).containsExactly(
            Triple(TODAY_CARD_ID, HomeCardSize.TALL, 100),
            Triple(COMING_UP_CARD_ID, HomeCardSize.WIDE, 50),
        ).inOrder()
        assertThat(capability.hasTab.first()).isTrue()
    }

    @Test
    fun itAddsConnectAndReviewToTheWizardAndCalendarsToSettings() = runTest {
        assertThat(capability.setupSteps().map { it.id to it.order }).containsExactly("calendar.connect" to 400, "calendar.review" to 410).inOrder()
        assertThat(capability.settingsPages().map { Triple(it.id, it.title, it.order) }).containsExactly(Triple("calendars", "Calendars", 400))
        val (connect, review) = capability.setupSteps()
        assertThat(connect.skippable).isTrue()
        assertThat(listOf(connect.done.first(), review.shown.first())).containsExactly(false, false)
        connect()
        assertThat(listOf(connect.done.first(), review.shown.first(), review.done.first())).containsExactly(true, true, true)
    }
}
