package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and android.util.Log.
@RunWith(AndroidJUnit4::class)
class CalendarReviewTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var people: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private val family = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val swim = CalendarSource("swim", "Mia's swimming", writable = false)
    private val chores = CalendarSource("chores", "Chores", writable = true)
    private val google = ScriptedProvider(
        "calendar.google", sourceList = listOf(family, swim, chores), features = setOf(Feature.READ, Feature.WRITE), displayName = "Google Calendar",
    )
    private val connection = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val toaster = RecordingToaster()

    @Before
    fun setUp() {
        ShadowLog.clear()
        calendar = calendarDb()
        people = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(people)
    }

    @After
    fun tearDown() {
        calendar.close()
        people.close()
    }

    private suspend fun TestScope.review(): Pair<CalendarReview, TestAccess> {
        val access = testAccess(household)
        val setup = CalendarSetup(store, setOf(google), { household.people.first() }, toaster, WallClock { 0L }, EmptyCoroutineContext)
        val connections = CalendarConnections(store, setup, access.control, setOf(google), backgroundScope)
        store.addConnection(connection, google.sourceList, emptyMap(), masterSourceId = family.id)
        return CalendarReview(store, setup, connections, household, access.control, toaster) to access
    }

    private suspend fun source(id: String): StoredSource = store.source("g1", id)!!

    @Test
    fun choosingWhoACalendarIsForTakesTheOpenSessionAndSaysSo() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        assertThat(review.setPerson(source("swim"), access.mia)).isTrue()
        assertThat(source("swim").mapping.person).isEqualTo(access.mia.id)
        assertThat(toaster.messages).containsExactly("Mia's swimming now shows as Mia")
        assertThat(access.requests.map { it.label }).containsExactly("Change settings")
    }

    @Test
    fun aPersonWhoIsGoneMeansFamily() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        val gone = Person(PersonId("gone"), "Gone", 0xFF000000)
        assertThat(review.setPerson(source("swim"), gone)).isTrue()
        assertThat(source("swim").mapping.person).isEqualTo(PersonId.FAMILY)
        assertThat(toaster.messages).containsExactly("Mia's swimming now shows as Family")
    }

    @Test
    fun hidingAndShowingIsSaid() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        review.setShown(source("swim"), shown = false)
        assertThat(source("swim").mapping.visible).isFalse()
        review.setShown(source("swim"), shown = true)
        assertThat(toaster.messages).containsExactly("Mia's swimming hidden", "Mia's swimming shown").inOrder()
    }

    @Test
    fun theMasterCantBeHidden() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        assertThat(review.setShown(source(family.id), shown = false)).isFalse()
        assertThat(source(family.id).mapping.visible).isTrue()
        assertThat(toaster.messages).containsExactly(COULD_NOT_SAVE)
    }

    @Test
    fun makingAnotherCalendarTheMasterSaysWhereNewEventsGo() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.ALEX)
        assertThat(review.makeMaster(source("chores"))).isTrue()
        assertThat(store.master().first()?.source?.id).isEqualTo("chores")
        assertThat(toaster.messages).containsExactly("New events now go to Chores")
    }

    @Test
    fun disconnectingCountsTheQueueThenRemovesEverything() = runTest {
        val (review, access) = review()
        repeat(3) { i -> store.enqueue(PendingChange(0, "g1", family.id, "e$i", ChangeKind.DELETE, null, 0, 0L, 0L)) }
        store.applyAccepted(
            "g1", family.id,
            RemoteEvent("e", "Walk", EventTime.Timed(Instant.ofEpochSecond(3_600)), EventTime.Timed(Instant.ofEpochSecond(7_200)), recurring = false),
            ZoneId.of("UTC"),
        )
        assertThat(store.eventsBetween(0, Long.MAX_VALUE).first()).hasSize(1)
        assertThat(review.queuedChanges("g1")).isEqualTo(3)
        val row = review.connections.first().single().row
        access.answer(TestAccess.ALEX)
        assertThat(review.disconnect(row)).isTrue()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.sources().first()).isEmpty()
        assertThat(store.eventsBetween(0, Long.MAX_VALUE).first()).isEmpty()
        assertThat(toaster.messages).containsExactly("Google Calendar disconnected")
    }

    @Test
    fun aCancelledPinChangesNothing() = runTest {
        val (review, access) = review()
        access.answer(null)
        assertThat(review.setShown(source("swim"), shown = false)).isFalse()
        assertThat(source("swim").mapping.visible).isTrue()
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun aChildCantChangeACalendar() = runTest {
        val (review, access) = review()
        access.answer(TestAccess.MIA, null)
        assertThat(review.setPerson(source("swim"), Person.Family)).isFalse()
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun aFailedCalendarChangeToastsAndLogsNoAccountOrCalendar() = runTest {
        val (review, access) = review()
        val swimming = source("swim")
        access.answer(TestAccess.ALEX)
        calendar.close()
        assertThat(review.setPerson(swimming, access.mia)).isFalse()
        assertThat(toaster.messages).containsExactly(COULD_NOT_SAVE)
        assertNoSecretsLogged("CalendarReview", listOf("family@example.com", "Mia's swimming", "swim"))
    }

    @Test
    fun connectingTheSameAccountTwiceKeepsOneConnection() = runTest {
        val access = testAccess(household)
        val setup = CalendarSetup(store, setOf(google), { household.people.first() }, toaster, WallClock { 0L }, EmptyCoroutineContext)
        val connections = CalendarConnections(store, setup, access.control, setOf(google), backgroundScope)
        // The wizard's Connect step finishes a connect this way; a second tap while the first sets up does the same.
        connections.finish(connection, reconnecting = false)
        connections.finish(connection, reconnecting = false)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (toaster.messages.size < 2) delay(10) } }
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Google Calendar reconnected").inOrder()
    }

    @Test
    fun theDisconnectQuestionCountsWhatIsDropped() {
        assertThat(disconnectQuestion("Google Calendar", 0)).isEqualTo("Disconnect Google Calendar? Its calendars leave the tablet.")
        assertThat(disconnectQuestion("Google Calendar", 1))
            .isEqualTo("Disconnect Google Calendar? Its calendars leave the tablet, and 1 change still waiting to sync is dropped.")
        assertThat(disconnectQuestion("Google Calendar", 3))
            .isEqualTo("Disconnect Google Calendar? Its calendars leave the tablet, and 3 changes still waiting to sync are dropped.")
    }
}
