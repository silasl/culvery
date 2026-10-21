package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
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
import uk.co.siland.culvery.core.plugin.WallClock

/** Read-only view of the cache as UI models, with queued changes laid over it. The UI never touches the network. */
@Singleton
class CalendarRepository @Inject constructor(
    private val store: CalendarStore,
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
    providers: Set<@JvmSuppressWildcards CalendarProvider>,
    writers: Set<@JvmSuppressWildcards CalendarWriter>,
    private val clock: WallClock = WallClock(System::currentTimeMillis),
) {
    private val writerIds = writers.map { it.providerId }.toSet()
    private val serviceNames = providers.associate { it.descriptor.id to it.descriptor.displayName }

    init {
        providers
            .filter { Feature.WRITE in it.descriptor.features && it.descriptor.id !in writerIds }
            .forEach { Log.w(TAG, "${it.descriptor.id} declares WRITE but binds no CalendarWriter; its events are read-only") }
    }

    val hasConnections: Flow<Boolean> = store.connectionIds().map { it.isNotEmpty() }.distinctUntilChanged()

    /** The household's people, for the detail sheet's chips for choosing who it's for and the add/edit sheet's Who chips. */
    val people: Flow<List<Person>> = household.people

    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        val service = { c: StoredConnection -> serviceNameOf(c.connection, serviceNames::get) }
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map(service),
            serviceNames = connections.map(service),
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
            reconnect = connections.firstOrNull { it.health == ConnectionHealth.NeedsSignIn }?.connection,
            problem = problemOf(connections, service),
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
            val order = people.map { it.id }
            val shown = overlayPending(events, pending, cat::source, z, from, to)
            (0 until count).map { i ->
                val date = start.plusDays(i.toLong())
                DayUi(
                    date,
                    shown.filter { it.event.isOn(date, z) }
                        .map { it.event.toUi(date, z, byId, order, cat, it.syncing) }
                        .sortedWith(compareByDescending<EventUi> { it.allDay }.thenBy { it.startSort }.thenBy { it.title }),
                )
            }
        }
    }
        // 4c §4.4: a pass that only records its time changes nothing on screen (P4); the mapping runs off Main (U7).
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun week(start: LocalDate): Flow<WeekUi> =
        combine(days(start, 7), household.people) { days, people ->
            WeekUi(start, days, people + Person.Family, spans = laneRows(weekSpans(days)))
        }.distinctUntilChanged()

    /**
     * What the editor's dots and clash check read (9 §3.1): today to the last day every connection's last sync covers
     * (no later than [lastShownDay]), with queued changes laid over it. A connection that needs signing in, is out of
     * date or has never synced, and a calendar the service refuses, mark their people unhealthy; on the master
     * calendar, where anyone's events are tagged, that is everyone.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun held(today: LocalDate): Flow<Held> = zone.zone.flatMapLatest { z ->
        val from = millis(today, z) - ZONE_MARGIN_MS
        val to = millis(lastShownDay(today).plusDays(1), z) + ZONE_MARGIN_MS
        combine(store.eventsBetween(from, to), store.pending(), store.sources(), store.connections(), household.peopleWithFamily) { events, pending, sources, connections, people ->
            val cat = SourceCatalog(sources, connections, writerIds, serviceNames)
            val byId = people.associateBy { it.id }
            val order = people.map { it.id }
            val synced = connections.mapNotNull { c -> c.lastSyncMillis?.let { Instant.ofEpochMilli(it).atZone(z).toLocalDate().plusDays(SYNC_FUTURE_DAYS) } }
            val lastDay = (synced + lastShownDay(today)).min()
            val shown = overlayPending(events, pending, cat::source, z, from, to)
                .map { it.event }
                .filter { e -> daySpan(e.start, e.end, z).let { (first, last) -> first <= lastDay && last >= today } }
                .map { it.toHeld(byId, order) }
            Held(shown, lastDay, unhealthyPeople(sources, connections, order, clock.nowMillis()), firstDay = today)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    /**
     * One event for the detail sheet, a queued create not yet synced included; null once it is gone or queued for
     * deletion. [today] makes "Today · …".
     */
    fun event(ref: EventRef, today: LocalDate): Flow<EventDetailUi?> = shownEvent(ref).map { s ->
        if (s == null) return@map null
        val e = s.shown.event
        val day = e.start.instantIn(s.zone).atZone(s.zone).toLocalDate()
        EventDetailUi(e.toUi(day, s.zone, s.people, s.order, s.catalog, s.shown.syncing), whenLabel(e.start, e.end, s.zone, today))
    }

    /** What the add/edit sheet starts from: [ref] as shown; null once it is gone or queued for deletion. */
    fun editable(ref: EventRef): Flow<EditableEvent?> = shownEvent(ref).map { s ->
        s?.shown?.event?.let { EditableEvent(ref, it.title, it.start, it.end, it.forPerson) }
    }

    private class Shown(val shown: ShownEvent, val catalog: SourceCatalog, val people: Map<PersonId, Person>, val order: List<PersonId>, val zone: ZoneId)

    /** [ref] with its queued changes laid over it; a queued create not yet in the mirror counts (2b-2 design D6). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun shownEvent(ref: EventRef): Flow<Shown?> = zone.zone.flatMapLatest { z ->
        combine(store.event(ref), store.pending(), catalog, household.peopleWithFamily) { stored, pending, cat, people ->
            val forIt = pending.filter { it.ref == ref || (it.kind == ChangeKind.ASSIGN_SERIES && it.remoteId == stored?.seriesId) }
            overlayPending(listOfNotNull(stored), forIt, cat::source, z, Long.MIN_VALUE, Long.MAX_VALUE)
                .singleOrNull()
                ?.let { Shown(it, cat, people.associateBy { p -> p.id }, people.map { p -> p.id }, z) }
        }
    }

    private fun millis(date: LocalDate, z: ZoneId) = date.atStartOfDay(z).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "CalendarRepository"

        /** Two days: more than any two zones' offsets differ. */
        const val ZONE_MARGIN_MS = 2 * 86_400_000L
    }
}

// Their events may be missing rather than absent (9 §3.1); Out of date is the week's rule.
private fun unhealthyPeople(sources: List<StoredSource>, connections: List<StoredConnection>, order: List<PersonId>, nowMillis: Long): Set<PersonId> {
    val failing = connections.filter {
        it.health == ConnectionHealth.NeedsSignIn || it.lastSyncMillis == null || isStale(it.lastSyncMillis, nowMillis)
    }.map { it.connection.id }.toSet()
    // A writable calendar's events can be tagged for anyone, so its trouble is everyone's.
    return sources.filter { it.mapping.visible && (it.connectionId in failing || it.readProblem != null) }
        .flatMapTo(mutableSetOf()) { if (it.isMaster || it.source.writable) order else listOf(it.mapping.person) } - PersonId.FAMILY
}

/** Signing in first (only the family can fix it), then the tablet offline, then a service that didn't answer. */
internal fun problemOf(connections: List<StoredConnection>, service: (StoredConnection) -> String): SyncProblem? =
    connections.firstOrNull { it.health == ConnectionHealth.NeedsSignIn }?.let { SyncProblem.SignIn(service(it)) }
        ?: SyncProblem.Offline.takeIf { connections.any { it.health == ConnectionHealth.Offline } }
        ?: connections.firstOrNull { it.health == ConnectionHealth.Unreachable }?.let { SyncProblem.NoAnswer(service(it)) }
