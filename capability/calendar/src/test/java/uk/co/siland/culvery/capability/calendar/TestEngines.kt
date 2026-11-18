package uk.co.siland.culvery.capability.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.house.EventReminders
import uk.co.siland.culvery.core.house.EventSeed
import uk.co.siland.culvery.core.house.EventTimes
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

// Every calendar test builds its editor and its engine here, so a constructor that grows touches one place. Calls
// stay on the test's dispatcher unless a test passes io; each gets its own write lock unless it shares one.

internal fun testEditor(
    store: CalendarStore,
    writers: Set<CalendarWriter>,
    access: AccessControl,
    toaster: Toaster,
    zone: HouseholdZone,
    clock: WallClock,
    scope: CoroutineScope,
    requestSync: () -> Unit = {},
    io: CoroutineContext = EmptyCoroutineContext,
    writeLock: CalendarWriteLock = CalendarWriteLock(),
    personOf: suspend (PersonId) -> Person? = { null },
    serviceOf: (providerId: String) -> String? = { null },
    order: suspend () -> List<PersonId> = { emptyList() },
    undone: UndoneKeys = UndoneKeys(InMemoryUndoneCreates()),
    shown: () -> WeeksShown = { WeeksShown.EIGHT },
    updates: UpdateRequests = UpdateRequests(),
    reminders: EventReminders? = null,
): CalendarEditor =
    CalendarEditor(store, writers, access, toaster, zone, clock, scope, requestSync, io, WRITE_ATTEMPT_MS, writeLock, personOf, serviceOf, order, undone, shown, updates, reminders)

/** Reminders as the calendar reaches them: each call recorded, [answer] given to gone and changed, or [fails] thrown. */
internal class RecordingReminders : EventReminders {
    val keys = MutableStateFlow(emptySet<String>())
    val opened = mutableListOf<EventSeed>()
    val gone = mutableListOf<List<String>>()
    val changed = mutableListOf<Changed>()
    var answer = false
    var fails: Exception? = null

    /** [toString] prints nothing, as the times and the key are the household's. */
    data class Changed(val key: String, val now: EventTimes, val startsAtMillis: Long?) {
        override fun toString() = "Changed"
    }

    override fun has(key: String): Flow<Boolean> = keys.map { key in it }

    override suspend fun open(host: OverlayHost, seed: EventSeed) {
        opened += seed
    }

    override suspend fun gone(keys: Collection<String>): Boolean {
        gone += keys.toList()
        fails?.let { throw it }
        return answer
    }

    override suspend fun changed(key: String, now: EventTimes, startsAtMillis: Long?): Boolean {
        changed += Changed(key, now, startsAtMillis)
        fails?.let { throw it }
        return answer
    }
}

/** The Calendar's setting, held in memory and read at once; [shown] is what it starts at. */
internal fun testCalendarSettings(shown: WeeksShown = WeeksShown.EIGHT): CalendarSettings {
    val store = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
    return CalendarSettings(store, CoroutineScope(Dispatchers.Unconfined)).also { runBlocking { it.setShown(shown) } }
}

internal fun testSync(
    store: CalendarStore,
    providers: Set<CalendarProvider>,
    zone: HouseholdZone,
    clock: WallClock,
    writers: Set<CalendarWriter>,
    toaster: Toaster,
    io: CoroutineContext = EmptyCoroutineContext,
    timeoutMillis: Long = PROVIDER_TIMEOUT_MS,
    writeLock: CalendarWriteLock = CalendarWriteLock(),
    refresher: SourceRefresher = SourceRefresher(store, { emptyList() }, clock, toaster, io, timeoutMillis),
    undone: UndoneKeys = UndoneKeys(InMemoryUndoneCreates()),
): CalendarSync = CalendarSync(store, providers, zone, clock, io, timeoutMillis, writers, toaster, writeLock, refresher, undone)
