package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature

/** Read-only view of the cache as UI models, with queued changes laid over it. The UI never touches the network. */
@Singleton
class CalendarRepository @Inject constructor(
    private val store: CalendarStore,
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
    providers: Set<@JvmSuppressWildcards CalendarProvider>,
    writers: Set<@JvmSuppressWildcards CalendarWriter>,
) {
    private val writerIds = writers.map { it.providerId }.toSet()
    private val serviceNames = providers.associate { it.descriptor.id to it.descriptor.displayName }

    init {
        providers
            .filter { Feature.WRITE in it.descriptor.features && it.descriptor.id !in writerIds }
            .forEach { Log.w(TAG, "${it.descriptor.id} declares WRITE but binds no CalendarWriter; its events are read-only") }
    }

    val hasConnections: Flow<Boolean> = store.connectionIds().map { it.isNotEmpty() }.distinctUntilChanged()

    /** The household's people, for the detail sheet's Assign chips and the add/edit sheet's Who chips. */
    val people: Flow<List<Person>> = household.people

    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map { it.connection.label },
            connectionLabels = connections.map { it.connection.label },
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
            reconnect = connections.firstOrNull { it.health == ConnectionHealth.NeedsSignIn }?.connection,
        )
    }.distinctUntilChanged()

    /**
     * The service name of the writable master calendar, where new events go (the connection label if its provider isn't
     * installed); null when there is nowhere to add, which hides the add entry points (2b-2 design §4.1).
     */
    val masterService: Flow<String?> = combine(store.master(), store.connections()) { master, connections ->
        writableMaster(master, connections, writerIds)?.connection?.let { serviceNameOf(it, serviceNames::get) }
    }.distinctUntilChanged()

    private val catalog: Flow<SourceCatalog> =
        combine(store.sources(), store.connections()) { sources, connections -> SourceCatalog(sources, connections, writerIds, serviceNames) }

    fun day(date: LocalDate): Flow<List<EventUi>> = days(date, 1).map { it.single().events }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = zone.zone.flatMapLatest { z ->
        // Wide enough for all-day rows synced in any other zone; each day then picks its own (4c C4).
        val from = millis(start, z) - ZONE_MARGIN_MS
        val to = millis(start.plusDays(count.toLong()), z) + ZONE_MARGIN_MS
        combine(store.eventsBetween(from, to), store.pending(), catalog, household.peopleWithFamily) { events, pending, cat, people ->
            val byId = people.associateBy { it.id }
            val shown = overlayPending(events, pending, cat::source, z, from, to)
            (0 until count).map { i ->
                val date = start.plusDays(i.toLong())
                DayUi(
                    date,
                    shown.filter { it.event.isOn(date, z) }
                        .map { it.event.toUi(date, z, byId, cat, it.syncing) }
                        .sortedWith(compareByDescending<EventUi> { it.allDay }.thenBy { it.startSort }.thenBy { it.title }),
                )
            }
        }
    }
        // 4c 4.4: a pass that only records its time changes nothing on screen (P4), and the mapping runs off Main (U7).
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun week(start: LocalDate): Flow<WeekUi> =
        combine(days(start, 7), household.people) { days, people -> WeekUi(start, days, people + Person.Family) }.distinctUntilChanged()

    /**
     * One event for the detail sheet, a queued create not yet synced included; null once it is gone or queued for
     * deletion. [today] makes "Today · …".
     */
    fun event(ref: EventRef, today: LocalDate): Flow<EventDetailUi?> = shownEvent(ref).map { s ->
        if (s == null) return@map null
        val e = s.shown.event
        val day = e.start.instantIn(s.zone).atZone(s.zone).toLocalDate()
        EventDetailUi(e.toUi(day, s.zone, s.people, s.catalog, s.shown.syncing), whenLabel(e.start, e.end, s.zone, today))
    }

    /** What the add/edit sheet starts from: [ref] as shown; null once it is gone or queued for deletion. */
    fun editable(ref: EventRef): Flow<EditableEvent?> = shownEvent(ref).map { s ->
        s?.shown?.event?.let { EditableEvent(ref, it.title, it.start, it.end, it.forPerson) }
    }

    private class Shown(val shown: ShownEvent, val catalog: SourceCatalog, val people: Map<PersonId, Person>, val zone: ZoneId)

    /** [ref] with its queued changes laid over it; a queued create not yet in the mirror counts (2b-2 design D6). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun shownEvent(ref: EventRef): Flow<Shown?> = zone.zone.flatMapLatest { z ->
        combine(store.event(ref), store.pending(), catalog, household.peopleWithFamily) { stored, pending, cat, people ->
            overlayPending(listOfNotNull(stored), pending.filter { it.ref == ref }, cat::source, z, Long.MIN_VALUE, Long.MAX_VALUE)
                .singleOrNull()
                ?.let { Shown(it, cat, people.associateBy { p -> p.id }, z) }
        }
    }

    private fun millis(date: LocalDate, z: ZoneId) = date.atStartOfDay(z).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "CalendarRepository"

        /** Two days: more than any two zones' offsets differ. */
        const val ZONE_MARGIN_MS = 2 * 86_400_000L
    }
}
