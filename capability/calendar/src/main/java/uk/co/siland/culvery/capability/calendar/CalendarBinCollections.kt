package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinCollections
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.HouseBin
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.HouseholdClock

/** Today's and tomorrow's bin days from the stored events of the calendars the wall shows; it makes no council read. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarBinCollections internal constructor(
    now: StateFlow<LocalDateTime?>,
    events: (fromMillis: Long, toMillis: Long) -> Flow<List<StoredEvent>>,
    rows: Flow<List<CalendarRow>>,
    colours: BinColours,
    zone: Flow<ZoneId>,
) : BinCollections {
    @Inject
    constructor(
        clock: HouseholdClock,
        store: CalendarStore,
        connections: CalendarConnections,
        colours: BinColours,
        zone: HouseholdZone,
    ) : this(clock.now, store::eventsBetween, connections.rows, colours, zone.zone)

    override val days: Flow<BinDays> = combine(now.map { it?.toLocalDate() }.distinctUntilChanged().filterNotNull(), zone, ::Pair)
        .distinctUntilChanged()
        .flatMapLatest { (date, z) ->
            val from = millis(date, z) - ZONE_MARGIN_MS
            val to = millis(date.plusDays(2), z) + ZONE_MARGIN_MS
            combine(events(from, to), rows, colours.current) { stored, connections, inks ->
                val binsRows = connections.filter { it.carriesBins }
                BinDays(
                    connected = binsRows.isNotEmpty(),
                    readFailed = binsRows.any { it.health != ConnectionHealth.Ok },
                    collections = collectionsOn(stored, listOf(date, date.plusDays(1)), z, inks),
                )
            }
        }
        .distinctUntilChanged()
}

private fun collectionsOn(events: List<StoredEvent>, dates: List<LocalDate>, zone: ZoneId, inks: Map<String, String>): List<BinCollection> {
    val kindsByDate = events.mapNotNull { e -> e.bins?.let { daySpan(e.start, e.end, zone).first to it } }
        .filter { (date, _) -> date in dates }
        .groupBy({ it.first }, { it.second })
    return dates.mapNotNull { date ->
        val kinds = kindsByDate[date]?.flatten() ?: return@mapNotNull null
        BinCollection(date, orderedBins(kinds).map { HouseBin(binLabel(it), binInkFor(inks, it)?.id) })
    }
}
