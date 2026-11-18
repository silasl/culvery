package uk.co.siland.culvery.capability.reminders

import androidx.room.withTransaction
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.reminders.db.ReminderEntity
import uk.co.siland.culvery.capability.reminders.db.ReminderStateEntity
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.HouseBin
import uk.co.siland.culvery.core.household.PersonId

@Singleton
class RemindersStore @Inject constructor(private val db: RemindersDatabase) {
    private val dao = db.remindersDao()

    val reminders: Flow<List<Reminder>> = dao.reminders().map { rows -> rows.map { it.toReminder() } }

    /** The defaults before the first write. */
    val state: Flow<ReminderState> = dao.state().map { it?.toState() ?: ReminderState() }

    /** Null with [REMINDERS_MAX] already stored. */
    suspend fun add(draft: ReminderDraft, madeBy: PersonId, nowMillis: Long): Long? = db.withTransaction {
        if (dao.count() >= REMINDERS_MAX) {
            null
        } else {
            dao.insert(draft.toEntity(madeBy, answeredAtMillis = nowMillis, snoozedUntilMillis = null, chimedAtMillis = null))
        }
    }

    /**
     * False when the reminder has gone. A new time, first day or rule restarts it, and a new first day or rule stops it
     * following its event's repeat; new words or people keep its marks. The form hands back an untouched rule as it was.
     */
    suspend fun change(id: Long, draft: ReminderDraft, nowMillis: Long): Boolean = db.withTransaction {
        val old = dao.byId(id) ?: return@withTransaction false
        val fresh = draft.toEntity(PersonId(old.madeBy), old.answeredAtMillis, old.snoozedUntilMillis, old.chimedAtMillis).copy(id = id)
        val newWhen = fresh.firstDate != old.firstDate || fresh.rule != old.rule
        val restarted = newWhen || fresh.minuteOfDay != old.minuteOfDay
        val kept = if (newWhen) fresh.copy(followsRepeat = false) else fresh
        dao.put(if (restarted) kept.copy(answeredAtMillis = nowMillis, snoozedUntilMillis = null) else kept)
        true
    }

    /** A one-off, or a repeat with no time after [nowMillis], is deleted; a repeat otherwise starts again from [nowMillis]. */
    suspend fun done(id: Long, nowMillis: Long, zone: ZoneId) = db.withTransaction {
        val row = dao.byId(id) ?: return@withTransaction
        if (row.rule == null || row.toReminder().nextDue(nowMillis, zone) == null) {
            dao.delete(id)
        } else {
            dao.put(row.copy(answeredAtMillis = nowMillis, snoozedUntilMillis = null))
        }
    }

    suspend fun later(id: Long, nowMillis: Long) = db.withTransaction {
        val row = dao.byId(id) ?: return@withTransaction
        dao.put(row.copy(snoozedUntilMillis = nowMillis + LATER_MS))
    }

    suspend fun chimed(ids: Collection<Long>, bins: Boolean, nowMillis: Long) = db.withTransaction {
        if (ids.isNotEmpty()) dao.chimed(ids, nowMillis)
        if (bins) updateState { it.copy(binsChimedAtMillis = nowMillis) }
    }

    /** False when it had already gone. */
    suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    /** Whether a reminder remembers the event with [key]. */
    fun hasEvent(key: String): Flow<Boolean> = dao.hasEvent(key).distinctUntilChanged()

    suspend fun byEvent(key: String): Reminder? = dao.byEvent(key)?.toReminder()

    /** How many reminders went with the events. */
    suspend fun deleteForEvents(keys: Collection<String>): Int = db.withTransaction {
        keys.distinct().chunked(KEYS_PER_QUERY).sumOf { dao.deleteForEvents(it) }
    }

    /** Reads the event's reminder, has [decide] say what becomes of it, and writes that, in one transaction; null when it has none. */
    internal suspend fun moveForEvent(key: String, decide: (Reminder) -> EventMove): EventMove? = db.withTransaction {
        val row = dao.byEvent(key) ?: return@withTransaction null
        decide(row.toReminder()).also { move ->
            when (move) {
                EventMove.None -> Unit
                EventMove.Delete -> dao.delete(row.id)
                is EventMove.To -> dao.update(move.reminder.toEntity())
            }
        }
    }

    suspend fun setBins(on: Boolean, time: LocalTime) = updateState { it.copy(binsOn = on, binsTime = time) }

    suspend fun binsDone(date: LocalDate) = updateState { it.copy(binsDoneFor = date) }

    suspend fun binsKept(collection: BinCollection?) = updateState { it.copy(binsKept = collection) }

    suspend fun binsLater(nowMillis: Long) = updateState { it.copy(binsSnoozedUntilMillis = nowMillis + LATER_MS) }

    /** Holding going from true to false stores [nowMillis] as when it ended. */
    suspend fun markHeld(held: Boolean, nowMillis: Long) = updateState {
        when {
            held == it.heldSeen -> it
            held -> it.copy(heldSeen = true)
            else -> it.copy(heldSeen = false, heldEndedAtMillis = nowMillis)
        }
    }

    private suspend fun updateState(change: (ReminderState) -> ReminderState) = db.withTransaction {
        val current = dao.stateNow()?.toState() ?: ReminderState()
        val next = change(current)
        if (next != current) dao.putState(next.toEntity())
    }
}

// Under SQLite's limit on a query's arguments.
private const val KEYS_PER_QUERY = 500

private fun Reminder.toEntity() =
    ReminderDraft(words, forPeople, time, schedule, event).toEntity(madeBy, answeredAtMillis, snoozedUntilMillis, chimedAtMillis).copy(id = id)

private fun ReminderDraft.toEntity(madeBy: PersonId, answeredAtMillis: Long, snoozedUntilMillis: Long?, chimedAtMillis: Long?) =
    ReminderEntity(
        words = words,
        forPeople = forPeople.joinToString(",") { it.value },
        madeBy = madeBy.value,
        minuteOfDay = time.minuteOfDay(),
        firstDate = schedule.first.toString(),
        rule = (schedule as? Schedule.Repeating)?.rule,
        answeredAtMillis = answeredAtMillis,
        snoozedUntilMillis = snoozedUntilMillis,
        chimedAtMillis = chimedAtMillis,
        eventKey = event?.key,
        eventDay = event?.day?.toString(),
        eventMinute = event?.time?.minuteOfDay(),
        eventRule = event?.rule,
        followsRepeat = event?.followsRepeat ?: false,
    )

private fun ReminderEntity.toReminder() = Reminder(
    id = id,
    words = words,
    forPeople = forPeople.split(',').filter { it.isNotEmpty() }.map(::PersonId),
    madeBy = PersonId(madeBy),
    time = timeOfMinute(minuteOfDay),
    schedule = LocalDate.parse(firstDate).let { first -> rule?.let { Schedule.Repeating(first, it) } ?: Schedule.Once(first) },
    answeredAtMillis = answeredAtMillis,
    snoozedUntilMillis = snoozedUntilMillis,
    chimedAtMillis = chimedAtMillis,
    event = eventKey?.let { EventLink(it, eventDay?.let(LocalDate::parse), eventMinute?.let(::timeOfMinute), eventRule, followsRepeat) },
)

private fun LocalTime.minuteOfDay() = hour * 60 + minute

private fun timeOfMinute(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)

private fun ReminderStateEntity.toState() = ReminderState(
    binsOn = binsOn,
    binsTime = timeOfMinute(binsMinuteOfDay),
    binsDoneFor = binsDoneFor?.let(LocalDate::parse),
    binsSnoozedUntilMillis = binsSnoozedUntilMillis,
    binsChimedAtMillis = binsChimedAtMillis,
    heldSeen = heldSeen,
    heldEndedAtMillis = heldEndedAtMillis,
    binsKept = binsKeptDate?.let { BinCollection(LocalDate.parse(it), decodeBins(binsKeptBins.orEmpty())) },
)

private fun ReminderState.toEntity() = ReminderStateEntity(
    binsOn = binsOn,
    binsMinuteOfDay = binsTime.minuteOfDay(),
    binsDoneFor = binsDoneFor?.toString(),
    binsSnoozedUntilMillis = binsSnoozedUntilMillis,
    binsChimedAtMillis = binsChimedAtMillis,
    heldSeen = heldSeen,
    heldEndedAtMillis = heldEndedAtMillis,
    binsKeptDate = binsKept?.date?.toString(),
    binsKeptBins = binsKept?.let { encodeBins(it.bins) },
)

private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")

private fun decode(text: String) = URLDecoder.decode(text, "UTF-8")

private fun encodeBins(bins: List<HouseBin>) = bins.joinToString(",") { "${encode(it.name)}:${encode(it.inkId.orEmpty())}" }

private fun decodeBins(text: String) = text.split(',').filter { it.isNotEmpty() }.map {
    val (name, ink) = it.split(':', limit = 2)
    HouseBin(decode(name), decode(ink).ifEmpty { null })
}
