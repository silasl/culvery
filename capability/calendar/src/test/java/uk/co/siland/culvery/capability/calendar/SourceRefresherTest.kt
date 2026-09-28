package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and android.util.Log.
@RunWith(AndroidJUnit4::class)
class SourceRefresherTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private var now = 1_000_000L
    private val toaster = RecordingToaster()
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val primary = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val swimming = CalendarSource("mia-swim", "Mia's swimming", writable = false)
    private val provider = ScriptedProvider(
        "calendar.google", sourceList = listOf(primary), features = setOf(Feature.READ, Feature.WRITE), displayName = "Google Calendar",
    )
    private val refresher by lazy { SourceRefresher(store, { listOf(mia) }, WallClock { now }, toaster, EmptyCoroutineContext, 1_000) }

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun connect(sources: List<CalendarSource> = listOf(primary), master: String = primary.id) =
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), sources, emptyMap(), masterSourceId = master)

    private suspend fun refresh() = refresher.refreshIfDue(provider, store.connectionsNow().single())

    private suspend fun sourceIds() = store.sources().first().map { it.source.id }

    @Test
    fun theFirstPassAfterTheAppStartsRefreshesEvenARecentCheckAndMapsNewCalendarsByName() = runTest {
        connect()
        // The process before this one checked a minute ago: only the app start makes this refresh due.
        store.refreshSources("g1", listOf(primary), now - 60_000) { SourceMapping.Default }
        provider.sourceList = listOf(primary, swimming)
        refresh()
        assertThat(store.source("g1", "mia-swim")!!.mapping).isEqualTo(SourceMapping(mia.id, visible = true))
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isEqualTo(now)
    }

    @Test
    fun theNextRefreshIsADayLater() = runTest {
        connect()
        refresh()
        provider.sourceList = listOf(primary, swimming)
        now += SOURCE_REFRESH_MS - 1
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id)
        now += 1
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }

    @Test
    fun aFlaggedConnectionIsRefreshedAtTheNextPass() = runTest {
        connect()
        refresh()
        provider.sourceList = listOf(primary, swimming)
        refresher.flag("g1", primary.id)
        now += 60_000
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }

    @Test
    fun aMasterDeletedInGoogleIsClearedWithOneToast() = runTest {
        // Google's primary can't be deleted, so the master that goes is another calendar made the master.
        val kids = CalendarSource("kids", "Kids", writable = true)
        connect(listOf(primary, kids, swimming), master = kids.id)
        provider.sourceList = listOf(primary, swimming)
        refresh()
        assertThat(store.master().first()).isNull()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
        refresher.flag("g1", swimming.id)
        refresh()
        assertThat(toaster.messages).containsExactly("Google Calendar: can't find the master calendar, so new events can't be added")
    }

    @Test
    fun aMasterThatBecameReadOnlyIsClearedWithOneToast() = runTest {
        connect()
        provider.sourceList = listOf(primary.copy(writable = false))
        refresh()
        assertThat(store.master().first()).isNull()
        assertThat(sourceIds()).containsExactly(primary.id)
        assertThat(toaster.messages).containsExactly("Google Calendar: can't find the master calendar, so new events can't be added")
    }

    @Test
    fun aListWithoutThePrimaryIsAFailedReadThatRemovesNothing() = runTest {
        connect(listOf(primary, swimming))
        // A bad answer (an empty body, say), never "every calendar was deleted".
        provider.sourceList = emptyList()
        refresh()
        provider.sourceList = listOf(swimming)
        refresher.flag("g1", swimming.id)
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
        assertThat(store.master().first()?.source?.id).isEqualTo(primary.id)
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isNull()
        assertThat(toaster.messages).isEmpty()
    }

    @Test
    fun aCalendarListThatFailsKeepsTheSourcesAndIsTriedAgain() = runTest {
        connect()
        provider.sourcesFailWith = UnreachableException("offline")
        provider.sourceList = listOf(primary, swimming)
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id)
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isNull()
        provider.sourcesFailWith = null
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }

    @Test
    fun aCheckInTheFutureAfterTheClockWasSetBackIsDue() = runTest {
        connect()
        refresh()
        provider.sourceList = listOf(primary, swimming)
        now -= 60_000
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id, swimming.id)
    }

    @Test
    fun aGoneSourceStillListedIsNotFlaggedAgainUntilTheDailyRefresh() = runTest {
        connect(listOf(primary, swimming))
        refresh()
        provider.sourceList = listOf(primary, swimming)
        refresher.flag("g1", swimming.id)
        refresh()
        val calls = provider.sourcesCalls
        refresher.flag("g1", swimming.id)
        now += 60_000
        refresh()
        assertThat(provider.sourcesCalls).isEqualTo(calls)
        now += SOURCE_REFRESH_MS
        refresh()
        refresher.flag("g1", swimming.id)
        now += 60_000
        refresh()
        assertThat(provider.sourcesCalls).isEqualTo(calls + 2)
    }

    @Test
    fun aCalendarListThatNeverAnswersTimesOutAndKeepsTheSources() = runTest {
        connect()
        provider.sourcesHang = true
        refresh()
        assertThat(sourceIds()).containsExactly(primary.id)
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isNull()
    }
}
