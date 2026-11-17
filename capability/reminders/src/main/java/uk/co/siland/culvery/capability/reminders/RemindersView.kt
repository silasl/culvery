package uk.co.siland.culvery.capability.reminders

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.HouseholdClock

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

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** `16:30` for today, else `Mon 16:30`. */
internal fun dueText(dueAtMillis: Long, nowMillis: Long, zone: ZoneId): String {
    val due = Instant.ofEpochMilli(dueAtMillis).atZone(zone)
    val time = due.format(CLOCK)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (due.toLocalDate() == today) time else "${due.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} $time"
}

/** The bins card for [key]'s collection, [kept] being what Reminders holds of it; null when it holds none. */
internal fun binsCard(key: DueKey.Bins, kept: BinCollection?, dueAtMillis: Long, nowMillis: Long, zone: ZoneId, waiting: Int): CardUi? {
    val collection = kept?.takeIf { it.date == key.date } ?: return null
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return CardUi(
        key = key,
        words = if (today >= key.date) "Bins out today" else "Bins out tonight",
        stripes = collection.bins.map { StripeUi(inkId = it.inkId) },
        who = "Bins",
        dueAt = dueText(dueAtMillis, nowMillis, zone),
        detail = collection.bins.joinToString { it.name },
        of = waiting.takeIf { it > 1 },
    )
}

/** The card the engine is showing, as the wall draws it; null while there is none to draw. */
@Singleton
class RemindersView @Inject constructor(
    store: RemindersStore,
    engine: RemindersEngine,
    household: HouseholdRepository,
    clock: HouseholdClock,
    zone: HouseholdZone,
) {
    // The minute is an input only so a card's due time gains its day at midnight.
    val card: Flow<CardUi?> = combine(engine.showing, store.reminders, store.state, household.people, clock.minutes) { showing, reminders, state, people, _ ->
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
    }.distinctUntilChanged()
}
