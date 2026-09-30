package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.Toaster

/** One connection as Review calendars shows it (4a design §4.5): its row, and its calendars. */
data class ReviewConnection(val row: CalendarRow, val sources: List<StoredSource>)

// 4a design §4.5.
fun nowShowsAs(calendar: String, person: String): String = "$calendar now shows as $person"

fun calendarHidden(calendar: String): String = "$calendar hidden"

fun calendarShown(calendar: String): String = "$calendar shown"

fun newEventsGoTo(calendar: String): String = "New events now go to $calendar"

fun disconnected(service: String): String = "$service disconnected"

/** One change reads in the singular (ruling 13). */
fun disconnectQuestion(service: String, queued: Int): String = "Disconnect $service? Its calendars leave the tablet" + when (queued) {
    0 -> "."
    1 -> ", and 1 change still waiting to sync is dropped."
    else -> ", and $queued changes still waiting to sync are dropped."
}

/**
 * Review calendars (4a design §3.9, D13, D14), for the wizard and Settings › Calendars: who each calendar is for, whether
 * it shows, the master, and disconnecting. Every change takes `settings.manage`, applies at once and on the tablet only,
 * and is toasted; a failure is "Couldn't save — try again." and changes nothing.
 */
@Singleton
class CalendarReview @Inject constructor(
    private val store: CalendarStore,
    private val setup: CalendarSetup,
    calendarConnections: CalendarConnections,
    private val household: HouseholdRepository,
    private val access: AccessControl,
    private val toaster: Toaster,
) {
    val connections: Flow<List<ReviewConnection>> = combine(calendarConnections.rows, store.sources()) { rows, sources ->
        rows.map { row -> ReviewConnection(row, sources.filter { it.connectionId == row.connection.id }) }
    }

    /** Family first, then the household: the person chips. */
    val people: Flow<List<Person>> = household.peopleWithFamily

    /** A person removed since the chips were drawn is Family: the store doesn't check that the person exists. */
    suspend fun setPerson(source: StoredSource, person: Person): Boolean = change {
        val target = household.peopleWithFamily.first().firstOrNull { it.id == person.id } ?: Person.Family
        store.setMapping(source.connectionId, source.source.id, target.id, source.mapping.visible)
        toaster.show(nowShowsAs(source.source.name, target.name))
    }

    suspend fun setShown(source: StoredSource, shown: Boolean): Boolean = change {
        store.setMapping(source.connectionId, source.source.id, source.mapping.person, shown)
        toaster.show(if (shown) calendarShown(source.source.name) else calendarHidden(source.source.name))
    }

    /** CalendarSetup.setMaster checks with the provider that the calendar can be written, then shows it. */
    suspend fun makeMaster(source: StoredSource): Boolean = change {
        setup.setMaster(source.connectionId, source.source.id)
        toaster.show(newEventsGoTo(source.source.name))
    }

    /** For the disconnect confirmation; changes nothing, so it asks for no PIN. */
    suspend fun queuedChanges(connectionId: String): Int = store.queuedChanges(connectionId)

    /** D14: the connection with its calendars, events and queued changes. The grant in the Google account stays. */
    suspend fun disconnect(row: CalendarRow): Boolean = change {
        store.removeConnection(row.connection.id)
        toaster.show(disconnected(row.service))
    }

    private suspend fun change(block: suspend () -> Unit): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        return try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The type only (P8): a message can hold a calendar id, which is often the account's email.
            Log.w(TAG, "Couldn't save a calendar change (${e::class.simpleName})")
            toaster.show(COULD_NOT_SAVE)
            false
        }
    }

    private companion object {
        const val TAG = "CalendarReview"
    }
}
