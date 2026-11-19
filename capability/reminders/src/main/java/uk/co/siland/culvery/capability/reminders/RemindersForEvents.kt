package uk.co.siland.culvery.capability.reminders

import android.util.Log
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.reminders.ui.openReminder
import uk.co.siland.culvery.core.house.EventReminders
import uk.co.siland.culvery.core.house.EventSeed
import uk.co.siland.culvery.core.house.EventTimes
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.repeat.movedTo
import uk.co.siland.culvery.core.repeat.ruleParts
import uk.co.siland.culvery.core.repeat.startable
import uk.co.siland.culvery.core.repeat.startedFrom

private const val TAG = "Reminders"
private const val LAST_MINUTE = 23 * 60 + 55

/**
 * Reminders' side of a reminder added from a calendar event (22 §10.4, §10.5). [gone] and [changed] come from the
 * calendar with no one signed in: they write with no PIN and no toast, on the application scope.
 */
@Singleton
class RemindersForEvents internal constructor(
    private val store: RemindersStore,
    private val actions: RemindersActions,
    private val members: Flow<List<Member>>,
    private val clock: WallClock,
    private val zone: HouseholdZone,
    private val scope: CoroutineScope,
) : EventReminders {
    @Inject
    constructor(
        store: RemindersStore,
        actions: RemindersActions,
        household: HouseholdRepository,
        clock: WallClock,
        zone: HouseholdZone,
        @ApplicationScope scope: CoroutineScope,
    ) : this(store, actions, household.members, clock, zone, scope)

    override fun has(key: String): Flow<Boolean> = store.hasEvent(key)

    override suspend fun open(host: OverlayHost, seed: EventSeed) = host.openReminder(null, actions, members, seed)

    override suspend fun gone(keys: Collection<String>): Boolean {
        if (keys.isEmpty()) return false
        return scope.async {
            (store.deleteForEvents(keys) > 0).also { if (it) Log.i(TAG, "event gone") }
        }.await()
    }

    override suspend fun changed(key: String, now: EventTimes, startsAtMillis: Long?): Boolean = scope.async {
        val nowMillis = clock.nowMillis()
        val zone = zone.current()
        when (store.moveForEvent(key) { it.afterEvent(now, startsAtMillis, nowMillis, zone) }) {
            null, EventMove.None -> false
            EventMove.Delete -> {
                Log.i(TAG, "event gone")
                false
            }
            is EventMove.To -> {
                Log.i(TAG, "event moved")
                true
            }
        }
    }.await()
}

internal sealed interface EventMove {
    data object None : EventMove

    data object Delete : EventMove

    data class To(val reminder: Reminder) : EventMove
}

/** What becomes of this reminder now its event's times are [now] (22 §10.5); [startsAtMillis] is the event's next start. */
internal fun Reminder.afterEvent(now: EventTimes, startsAtMillis: Long?, nowMillis: Long, zone: ZoneId): EventMove {
    val link = event ?: return EventMove.None
    if (link.day == now.day && link.time == now.time && link.rule == now.rule) return EventMove.None
    val shift = shiftBetween(link, now)
    val follows = link.followsRepeat && (link.rule != null || now.rule != null)
    val shifted = schedule.first.atTime(time).plus(shift)
    var first = if (follows) schedule.first else shifted.toLocalDate()
    val newTime = if (follows) followerTime(time, link.time, now.time) else shifted.toLocalTime()
    var rule = (schedule as? Schedule.Repeating)?.rule
    val dayMoved = link.day != null && now.day != null && link.day != now.day
    if (follows && (link.rule != now.rule || dayMoved)) {
        val newRule = now.rule
        when {
            newRule == null -> {
                first = now.day ?: shifted.toLocalDate()
                rule = null
            }
            !startable(newRule, now.day != null, zone) -> Unit
            else -> {
                val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                val (from, left) = startedFrom(newRule, now.day ?: first, today, zone) ?: return EventMove.Delete
                first = from
                rule = left
            }
        }
    } else if (!follows && rule != null && first != schedule.first) {
        rule = movedRule(rule, schedule.first, first)
    }
    val moved = rule?.let { Schedule.Repeating(first, it) } ?: Schedule.Once(first)
    if (startsAtMillis == null || (moved is Schedule.Once && startsAtMillis <= nowMillis)) return EventMove.Delete
    val ownNext = moved.nextAt(newTime, answeredAtMillis + shift.toMillis(), zone)
    val answered = if (ownNext != null && ownNext <= nowMillis) ownNext - 1 else nowMillis
    return EventMove.To(
        copy(
            time = newTime,
            schedule = moved,
            answeredAtMillis = answered,
            snoozedUntilMillis = null,
            event = link.copy(day = now.day, time = now.time, rule = now.rule),
        ),
    )
}

/** The days moved, when both are known, plus the time moved, when neither side is all day. */
private fun shiftBetween(link: EventLink, now: EventTimes): Duration {
    val oldDay = link.day
    val newDay = now.day
    val days = if (oldDay != null && newDay != null) Duration.ofDays(ChronoUnit.DAYS.between(oldDay, newDay)) else Duration.ZERO
    return days.plusMinutes(timeShift(link.time, now.time).toLong())
}

/** Minutes between two times of day; none when either is all day. */
private fun timeShift(from: LocalTime?, to: LocalTime?): Int =
    if (from == null || to == null) 0 else to.minuteOfDay() - from.minuteOfDay()

/** A reminder that follows the repeat keeps its day: its time moves as the event's did, stopping at 00:00 or 23:55. */
private fun followerTime(time: LocalTime, from: LocalTime?, to: LocalTime?): LocalTime {
    val minutes = time.minuteOfDay() + timeShift(from, to)
    return minutes.coerceIn(0, LAST_MINUTE).let { LocalTime.of(it / 60, it % 60) }
}

/** A rule the picker can show, with its weekdays in it, written again from [to]; its other days move as far. */
private fun movedRule(rule: String, from: LocalDate, to: LocalDate): String =
    if (ruleParts(rule)?.containsKey("BYDAY") != true) rule else movedTo(rule, from, to) ?: rule
