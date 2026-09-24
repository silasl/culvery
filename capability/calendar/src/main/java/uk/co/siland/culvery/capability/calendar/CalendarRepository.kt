package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
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

    init {
        providers
            .filter { Feature.WRITE in it.descriptor.features && it.descriptor.id !in writerIds }
            .forEach { Log.w(TAG, "${it.descriptor.id} declares WRITE but binds no CalendarWriter; its events are read-only") }
    }

    val hasConnections: Flow<Boolean> = store.connectionIds().map { it.isNotEmpty() }.distinctUntilChanged()

    /** The household's people, for the detail sheet's Assign chips. */
    val people: Flow<List<Person>> = household.people

    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map { it.connection.label },
            connectionLabels = connections.map { it.connection.label },
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
        )
    }.distinctUntilChanged()

    private val catalog: Flow<SourceCatalog> =
        combine(store.sources(), store.connections()) { sources, connections -> SourceCatalog(sources, connections, writerIds) }

    fun day(date: LocalDate): Flow<List<EventUi>> = days(date, 1).map { it.single().events }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = zone.zone.flatMapLatest { z ->
        val from = millis(start, z)
        val to = millis(start.plusDays(count.toLong()), z)
        combine(store.eventsBetween(from, to), store.pending(), catalog, household.peopleWithFamily) { events, pending, cat, people ->
            val byId = people.associateBy { it.id }
            val shown = overlayPending(events, pending, cat::source, z, from, to)
            (0 until count).map { i ->
                val date = start.plusDays(i.toLong())
                val dayStart = millis(date, z)
                val dayEnd = millis(date.plusDays(1), z)
                DayUi(
                    date,
                    shown.filter { spanOverlaps(it.event.startSort, it.event.endSort, dayStart, dayEnd) }
                        .map { it.event.toUi(date, z, byId, cat, it.syncing) }
                        .sortedWith(compareByDescending<EventUi> { it.allDay }.thenBy { it.startSort }.thenBy { it.title }),
                )
            }
        }
    }

    fun week(start: LocalDate): Flow<WeekUi> =
        combine(days(start, 7), household.people) { days, people -> WeekUi(start, days, people + Person.Family) }

    /** One event for the detail sheet; null once it is gone or queued for deletion. [today] makes "Today · …". */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun event(ref: EventRef, today: LocalDate): Flow<EventDetailUi?> = zone.zone.flatMapLatest { z ->
        combine(store.event(ref), store.pending(), catalog, household.peopleWithFamily) { stored, pending, cat, people ->
            val mirrored = stored ?: return@combine null
            val shown = overlayPending(listOf(mirrored), pending.filter { it.ref == ref }, cat::source, z, Long.MIN_VALUE, Long.MAX_VALUE)
                .singleOrNull() ?: return@combine null
            val e = shown.event
            val day = e.start.instantIn(z).atZone(z).toLocalDate()
            EventDetailUi(e.toUi(day, z, people.associateBy { it.id }, cat, shown.syncing), whenLabel(e.start, e.end, z, today))
        }
    }

    private fun millis(date: LocalDate, z: ZoneId) = date.atStartOfDay(z).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "CalendarRepository"
    }
}
