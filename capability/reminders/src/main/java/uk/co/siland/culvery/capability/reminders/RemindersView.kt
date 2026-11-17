package uk.co.siland.culvery.capability.reminders

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinCollections
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.ui.clockText

/** A person's [colour], or a bin's [inkId]; neither draws as an unknown bin. */
data class StripeUi(val colour: Long? = null, val inkId: String? = null)

/** What the card draws. [of] is set only when more than one waits. [toString] leaves out all but the key. */
data class CardUi(
    val key: DueKey,
    val words: String,
    val stripes: List<StripeUi>,
    val who: String,
    val dueAt: String,
    val detail: String? = null,
    val of: Int? = null,
) {
    override fun toString() = "CardUi($key)"
}

/**
 * One line of the tab. [key] is set on a waiting row (its Done button), [id] on the family's own reminder (a tap opens it).
 * [note] is `Back at 18:30` on a snoozed waiting row, else the repeat words. [toString] leaves out all but the key and id.
 */
data class RowUi(
    val key: DueKey?,
    val id: Long?,
    val stripes: List<StripeUi>,
    val time: String,
    val words: String,
    val who: String,
    val note: String?,
) {
    override fun toString() = "RowUi($key, $id)"
}

/** The bins reminder's row: whether it is on, its time, and whether the calendar's bin dates can't be read. */
data class BinsRowUi(val on: Boolean, val time: LocalTime, val cantRead: Boolean) {
    override fun toString() = "BinsRowUi"
}

const val CANT_READ_BINS = "Can’t read the bin dates"

fun binsRowTitle(time: LocalTime) = "Bins · The evening before a collection, ${clockText(time)}"

data class TabUi(val waiting: List<RowUi>, val today: List<RowUi>, val comingUp: List<RowUi>, val bins: BinsRowUi?)

/** `16:30` for today, else `Mon 16:30`. */
internal fun dueText(dueAtMillis: Long, nowMillis: Long, zone: ZoneId): String {
    val due = Instant.ofEpochMilli(dueAtMillis).atZone(zone)
    val time = clockText(due.toLocalTime())
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (due.toLocalDate() == today) time else "${due.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} $time"
}

/** The bins card for [key]'s collection, [kept] being what Reminders holds of it; null when it holds none. */
internal fun binsCard(key: DueKey.Bins, kept: BinCollection?, dueAtMillis: Long, nowMillis: Long, zone: ZoneId, waiting: Int): CardUi? {
    val collection = kept?.takeIf { it.date == key.date } ?: return null
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return CardUi(
        key = key,
        words = binsWords(key.date, today),
        stripes = collection.bins.map { StripeUi(inkId = it.inkId) },
        who = BINS_WHO,
        dueAt = dueText(dueAtMillis, nowMillis, zone),
        detail = collection.bins.joinToString { it.name },
        of = waiting.takeIf { it > 1 },
    )
}

private fun backAt(due: Due, nowMillis: Long, zone: ZoneId): String? {
    val until = due.snoozedUntilMillis?.takeIf { due.snoozed(nowMillis) } ?: return null
    return "Back at ${clockText(Instant.ofEpochMilli(until).atZone(zone).toLocalTime())}"
}

/** A waiting repeat is under Waiting only; the others sit under Today or Coming up by their next time. */
internal fun tabOf(
    dues: List<Due>,
    reminders: List<Reminder>,
    state: ReminderState,
    people: List<Person>,
    nowMillis: Long,
    zone: ZoneId,
    bins: BinDays?,
): TabUi {
    val byId = reminders.associateBy { it.id }

    fun row(r: Reminder, time: String, key: DueKey?, note: String?): RowUi {
        val who = forWho(r.forPeople, people)
        return RowUi(key, r.id, who.map { StripeUi(colour = it.color) }, time, r.words, HouseLines.roomList(who.map { it.name }), note)
    }

    val waiting = dues.mapNotNull { due ->
        val back = backAt(due, nowMillis, zone)
        when (val key = due.key) {
            is DueKey.Mine -> byId[key.id]?.let { row(it, dueText(due.dueAtMillis, nowMillis, zone), key, back) }
            is DueKey.Bins -> binsCard(key, state.binsKept, due.dueAtMillis, nowMillis, zone, 1)?.let {
                RowUi(key, null, it.stripes, it.dueAt, it.words, it.who, back ?: it.detail)
            }
        }
    }
    val waitingIds = dues.mapNotNull { (it.key as? DueKey.Mine)?.id }.toSet()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val next = reminders.filter { it.id !in waitingIds }
        .mapNotNull { r -> r.nextDue(nowMillis, zone)?.let { r to it } }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
    val (todays, later) = next.partition { Instant.ofEpochMilli(it.second).atZone(zone).toLocalDate() == today }
    fun rows(list: List<Pair<Reminder, Long>>) = list.map { (r, at) -> row(r, dueText(at, nowMillis, zone), null, repeatWords(r.schedule)) }
    val binsRow = bins?.takeIf { it.connected }?.let { BinsRowUi(state.binsOn, state.binsTime, it.readFailed) }
    return TabUi(waiting, rows(todays), rows(later), binsRow)
}

/** The card the engine is showing, as the wall draws it; null while there is none to draw. */
@Singleton
class RemindersView @Inject constructor(
    store: RemindersStore,
    engine: RemindersEngine,
    household: HouseholdRepository,
    clock: HouseholdClock,
    zone: HouseholdZone,
    bins: Optional<BinCollections>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val binDays: Flow<BinDays?> = bins.binDays()

    // The minute is an input only so a card's due time gains its day at midnight.
    val card: StateFlow<CardUi?> = combine(engine.showing, store.reminders, store.state, household.people, clock.minutes) { showing, reminders, state, people, _ ->
        showing?.let {
            val now = clock.nowMillis()
            val tz = zone.current()
            when (val key = it.due.key) {
                is DueKey.Bins -> binsCard(key, state.binsKept, it.due.dueAtMillis, now, tz, it.of)
                is DueKey.Mine -> reminders.firstOrNull { r -> r.id == key.id }?.let { r ->
                    val who = forWho(r.forPeople, people)
                    CardUi(
                        key = key,
                        words = r.words,
                        stripes = who.map { p -> StripeUi(colour = p.color) },
                        who = HouseLines.roomList(who.map { p -> p.name }),
                        dueAt = dueText(it.due.dueAtMillis, now, tz),
                        of = it.of.takeIf { n -> n > 1 },
                    )
                }
            }
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    // The minute is an input so the groups regroup at midnight with no write to the store.
    private val binDaysEachMinute = combine(binDays, clock.minutes) { days, _ -> days }

    val tab: Flow<TabUi> = combine(engine.waiting, store.reminders, store.state, household.people, binDaysEachMinute) { dues, reminders, state, people, days ->
        tabOf(dues, reminders, state, people, clock.nowMillis(), zone.current(), days)
    }.distinctUntilChanged()
}
