package uk.co.siland.culvery.capability.reminders

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinCollections
import uk.co.siland.culvery.core.house.BinDays

const val BINS_WHO = "Bins"
const val PICK_A_BINS_TIME = "Pick a time from midday until night hours start."

private val NOON = LocalTime.NOON

/** The calendar's bin days, or null days with no calendar capability. */
internal fun Optional<BinCollections>.binDays(): Flow<BinDays?> = map<Flow<BinDays?>> { it.days }.orElse(flowOf(null))

/** The moment a collection's reminder clears. */
internal fun binsClearsAt(date: LocalDate, zone: ZoneId): Long = timeOn(date, NOON, zone)

/**
 * The bins reminder waiting now (22 §4.7), with the collection it is for. The candidates are the calendar's collections and
 * the one Reminders kept when the calendar has dropped it; the first by date that is on, started, not cleared and not Done wins.
 */
internal fun binsDue(days: BinDays?, state: ReminderState, nowMillis: Long, zone: ZoneId): Pair<Due, BinCollection>? {
    if (days == null || !days.connected || !state.binsOn) return null
    val kept = state.binsKept?.takeIf { k -> days.collections.none { it.date == k.date } }
    for (collection in (days.collections + listOfNotNull(kept)).sortedBy { it.date }) {
        val startsAt = timeOn(collection.date.minusDays(1), state.binsTime, zone)
        if (nowMillis in startsAt until binsClearsAt(collection.date, zone) && state.binsDoneFor != collection.date) {
            return Due(DueKey.Bins(collection.date), startsAt, state.binsSnoozedUntilMillis, state.binsChimedAtMillis) to collection
        }
    }
    return null
}

internal fun binsWords(date: LocalDate, today: LocalDate): String = if (today < date) "Bins out tonight" else "Bins out today"

/** From midday, and before night hours start when that is known and is later than midday. */
internal fun binsTimeAllowed(time: LocalTime, nightStarts: LocalTime?): Boolean =
    time >= NOON && (nightStarts == null || nightStarts <= NOON || time < nightStarts)
