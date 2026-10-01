package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/**
 * Keeps the calendar in step with the household (4a design §3.8, §3.9): a calendar mapped to someone removed shows as
 * Family, and a change of the household's time zone asks for a sync (all-day events are stored in the zone they were
 * synced in).
 */
@Singleton
class HouseholdFollower @Inject constructor(
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
    private val store: CalendarStore,
    private val setup: CalendarSetup,
    @ApplicationScope private val scope: CoroutineScope,
) : Startable {
    /** Completes once the zone it starts with is read; the tests wait on it. */
    internal val zoneRead = CompletableDeferred<Unit>()

    override fun start() {
        scope.launch {
            household.people
                // The type only: a message could hold a name.
                .retryWithBackoff { Log.w(TAG, "Couldn't read the household's people; retrying (${it::class.simpleName})") }
                // A rename or recolour leaves nobody's calendars to move.
                .map { people -> people.mapTo(HashSet()) { it.id } }
                .distinctUntilChanged()
                .collect { ids ->
                    try {
                        store.remapMissingPeople(ids)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't move a removed person's calendars to Family (${e::class.simpleName})")
                    }
                }
        }
        scope.launch {
            zone.zone
                .retryWithBackoff { Log.w(TAG, "Couldn't read the household's time zone; retrying (${it::class.simpleName})") }
                .distinctUntilChanged()
                .onEach { zoneRead.complete(Unit) }
                // The first value is the zone at start, which the sync loop already uses.
                .drop(1)
                .collect { setup.syncSoon() }
        }
    }

    private companion object {
        const val TAG = "HouseholdFollower"
    }
}
