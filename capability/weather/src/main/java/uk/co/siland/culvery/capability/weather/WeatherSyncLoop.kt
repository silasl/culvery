package uk.co.siland.culvery.capability.weather

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/** The capability's one log tag. */
internal const val TAG = "Weather"

const val WEATHER_REFRESH_MS = 30 * 60_000L
const val WEATHER_RETRY_MS = 5 * 60_000L

/** The place to fetch for: a renamed town is the same place. */
internal fun Flow<HomeLocation?>.places(): Flow<WeatherPlace?> = map { location -> location?.let(::WeatherPlace) }

/**
 * Fetches as soon as a location is known and whenever its coordinates or zone change, then every
 * [WEATHER_REFRESH_MS], or [WEATHER_RETRY_MS] after a failure (4b design §3.6). Without a location it waits and fetches
 * nothing. A change that arrives mid-fetch runs one more fetch afterwards; it never cancels the running one.
 */
@Singleton
class WeatherSyncLoop internal constructor(
    private val fetch: suspend (WeatherPlace) -> Unit,
    private val places: Flow<WeatherPlace?>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(fetcher: WeatherFetcher, household: HouseholdRepository, @ApplicationScope scope: CoroutineScope) :
        this(fetcher::fetch, household.location.places(), scope)

    override fun start() {
        scope.launch {
            val latest = MutableStateFlow<WeatherPlace?>(null)
            val wake = Channel<Unit>(Channel.CONFLATED)
            launch {
                // Distinct after the retry: a read that fails after its value re-emits it on every retry (plan review 3).
                places.retryWithBackoff { Log.w(TAG, "Couldn't read the home location (${it::class.simpleName}); retrying") }
                    .distinctUntilChanged()
                    .collect {
                        latest.value = it
                        wake.trySend(Unit)
                    }
            }
            // Null: wait for a location, with no timer.
            var wait: Long? = null
            while (true) {
                val due = wait
                if (due == null) wake.receive() else withTimeoutOrNull(due) { wake.receive() }
                val place = latest.value
                wait = when {
                    place == null -> null
                    runFetch(place) -> WEATHER_REFRESH_MS
                    else -> WEATHER_RETRY_MS
                }
            }
        }
    }

    private suspend fun runFetch(place: WeatherPlace): Boolean =
        try {
            fetch(place)
            true
        } catch (e: CancellationException) {
            // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "A weather fetch was cancelled internally")
            false
        } catch (e: Throwable) {
            // An Error too, as the calendar's loop. The type only: a message could hold the place.
            Log.w(TAG, "Weather fetch failed (${e::class.simpleName})")
            false
        }
}
