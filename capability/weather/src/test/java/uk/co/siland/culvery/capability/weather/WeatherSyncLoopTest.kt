package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric only because the loop logs through android.util.Log.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WeatherSyncLoopTest {
    private val london = WeatherPlace(LONDON)
    private val leeds = WeatherPlace(LEEDS)

    @Before
    fun setUp() = ShadowLog.clear()

    private fun TestScope.loop(places: Flow<WeatherPlace?>, fetch: suspend (WeatherPlace) -> Unit) =
        WeatherSyncLoop(fetch, places, backgroundScope).start()

    private fun TestScope.after(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun itFetchesAtOnceWhenALocationIsKnownThenEveryThirtyMinutes() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        loop(MutableStateFlow(london)) { fetched += it }
        runCurrent()
        assertThat(fetched).containsExactly(london)
        after(WEATHER_REFRESH_MS - 1)
        assertThat(fetched).hasSize(1)
        after(1)
        assertThat(fetched).containsExactly(london, london)
    }

    @Test
    fun withoutALocationItFetchesNothingUntilOneIsSet() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(null)
        loop(places) { fetched += it }
        runCurrent()
        after(3 * WEATHER_REFRESH_MS)
        assertThat(fetched).isEmpty()
        places.value = london
        runCurrent()
        assertThat(fetched).containsExactly(london)
    }

    @Test
    fun aLocationChangeFetchesAtOnce() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(london)
        loop(places) { fetched += it }
        runCurrent()
        after(60_000)
        places.value = leeds
        runCurrent()
        assertThat(fetched).containsExactly(london, leeds).inOrder()
    }

    @Test
    fun aRenamedTownIsNotFetchedAgain() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val locations = MutableStateFlow<HomeLocation?>(LONDON)
        loop(locations.places()) { fetched += it }
        runCurrent()
        locations.value = LONDON.copy(name = "Westminster")
        runCurrent()
        assertThat(fetched).hasSize(1)
        locations.value = LONDON.copy(timeZoneId = "Europe/Dublin")
        runCurrent()
        assertThat(fetched).hasSize(2)
    }

    @Test
    fun afterAFailureTheNextTryIsInFiveMinutesThenThirtyAfterASuccess() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            if (calls == 1) throw WeatherUnavailableException("offline")
        }
        runCurrent()
        assertThat(calls).isEqualTo(1)
        after(WEATHER_RETRY_MS - 1)
        assertThat(calls).isEqualTo(1)
        after(1)
        assertThat(calls).isEqualTo(2)
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
        after(WEATHER_REFRESH_MS - WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun repeatedFailuresTryAtMostOnceEveryFiveMinutes() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            throw WeatherUnavailableException("offline")
        }
        runCurrent()
        after(60 * 60_000L)
        assertThat(calls).isEqualTo(1 + 12)
    }

    @Test
    fun anErrorIsLoggedByTypeAndTheLoopGoesOn() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            if (calls == 1) throw StackOverflowError("a provider recursed")
        }
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
        assertThat(ShadowLog.getLogs().filter { it.tag == TAG }.map { it.msg }).contains("Weather fetch failed (StackOverflowError)")
    }

    @Test
    fun aStrayCancellationDoesNotStopTheLoop() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            if (calls == 1) throw CancellationException("stray")
        }
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
    }

    /** Review Focus 1: the running fetch isn't cancelled; the new place is fetched straight after it. */
    @Test
    fun aLocationChangeMidFetchFetchesTheNewPlaceRightAfter() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(london)
        loop(places) {
            fetched += it
            if (fetched.size == 1) gate.await()
        }
        runCurrent()
        places.value = leeds
        runCurrent()
        assertThat(fetched).containsExactly(london)
        gate.complete(Unit)
        runCurrent()
        assertThat(fetched).containsExactly(london, leeds).inOrder()
    }

    /** Plan review 3: a location read that keeps failing after its value must not wake the loop on each retry. */
    @Test
    fun aFlakyLocationReadFetchesOnce() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = flow {
            emit(london)
            throw IllegalStateException("store hiccup")
        }
        loop(places) { fetched += it }
        runCurrent()
        after(60_000)
        assertThat(fetched).containsExactly(london)
    }

    @Test
    fun aLocationReadThatFailsIsRetried() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        var reads = 0
        val places = flow {
            if (reads++ == 0) throw IllegalStateException("store hiccup")
            emit(london)
        }
        loop(places) { fetched += it }
        runCurrent()
        assertThat(fetched).isEmpty()
        after(1_000)
        assertThat(fetched).containsExactly(london)
    }

    @Test
    fun nothingLoggedHoldsThePlace() = runTest {
        loop(MutableStateFlow(london)) { throw WeatherUnavailableException("offline at 51.5074,-0.1278 Europe/London") }
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertNoSecretsLogged(TAG, listOf("51.5074", "-0.1278", "Europe/London", "London"), minLines = 2)
    }

    /** The store rethrows without logging; the loop logs the type alone, never the place the failed write held. */
    @Test
    fun aStoreFailureIsLoggedByTypeAlone() = runTest {
        val db = weatherDb()
        val fetcher = WeatherFetcher(setOf(ScriptedWeatherProvider()), WeatherStore(db), WallClock { 5_000L })
        db.close()
        loop(MutableStateFlow(london)) { fetcher.fetch(it) }
        // Room fails on its own thread.
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (ShadowLog.getLogs().none { it.tag == TAG }) delay(10) } }
        val logged = ShadowLog.getLogs().filter { it.tag == TAG }
        // The waiting test also lets virtual time run, so the five-minute retries add lines; each is the same.
        assertThat(logged.map { it.msg }.distinct()).containsExactly("Weather fetch failed (IllegalStateException)")
        assertThat(logged.mapNotNull { it.throwable }).isEmpty()
        assertNoSecretsLogged(TAG, listOf("51.5074", "-0.1278", "Europe/London", "London"))
    }
}
