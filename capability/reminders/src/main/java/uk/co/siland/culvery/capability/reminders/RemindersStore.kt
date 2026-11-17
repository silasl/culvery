package uk.co.siland.culvery.capability.reminders

import androidx.room.withTransaction
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
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

    /** False when the reminder has gone. A new time, day or weekdays restart it; new words or people keep its marks. */
    suspend fun change(id: Long, draft: ReminderDraft, nowMillis: Long): Boolean = db.withTransaction {
        val old = dao.byId(id) ?: return@withTransaction false
        val fresh = draft.toEntity(PersonId(old.madeBy), old.answeredAtMillis, old.snoozedUntilMillis, old.chimedAtMillis).copy(id = id)
        val restarted = fresh.minuteOfDay != old.minuteOfDay || fresh.onDate != old.onDate || fresh.weekdays != old.weekdays
        dao.put(if (restarted) fresh.copy(answeredAtMillis = nowMillis, snoozedUntilMillis = null) else fresh)
        true
    }

    /** A one-off is deleted; a repeat starts again from [nowMillis]. */
    suspend fun done(id: Long, nowMillis: Long) = db.withTransaction {
        val row = dao.byId(id) ?: return@withTransaction
        if (row.weekdays == 0) dao.delete(id) else dao.put(row.copy(answeredAtMillis = nowMillis, snoozedUntilMillis = null))
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

private fun weekdayMask(days: Set<DayOfWeek>) = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun weekdaysOf(mask: Int) = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

private fun ReminderDraft.toEntity(madeBy: PersonId, answeredAtMillis: Long, snoozedUntilMillis: Long?, chimedAtMillis: Long?) =
    ReminderEntity(
        words = words,
        forPeople = forPeople.joinToString(",") { it.value },
        madeBy = madeBy.value,
        minuteOfDay = time.hour * 60 + time.minute,
        onDate = (schedule as? Schedule.Once)?.date?.toString(),
        weekdays = (schedule as? Schedule.Weekly)?.let { weekdayMask(it.days) } ?: 0,
        answeredAtMillis = answeredAtMillis,
        snoozedUntilMillis = snoozedUntilMillis,
        chimedAtMillis = chimedAtMillis,
    )

private fun ReminderEntity.toReminder() = Reminder(
    id = id,
    words = words,
    forPeople = forPeople.split(',').filter { it.isNotEmpty() }.map(::PersonId),
    madeBy = PersonId(madeBy),
    time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
    schedule = onDate?.let { Schedule.Once(LocalDate.parse(it)) } ?: Schedule.Weekly(weekdaysOf(weekdays)),
    answeredAtMillis = answeredAtMillis,
    snoozedUntilMillis = snoozedUntilMillis,
    chimedAtMillis = chimedAtMillis,
)

private fun ReminderStateEntity.toState() = ReminderState(
    binsOn = binsOn,
    binsTime = LocalTime.of(binsMinuteOfDay / 60, binsMinuteOfDay % 60),
    binsDoneFor = binsDoneFor?.let(LocalDate::parse),
    binsSnoozedUntilMillis = binsSnoozedUntilMillis,
    binsChimedAtMillis = binsChimedAtMillis,
    heldSeen = heldSeen,
    heldEndedAtMillis = heldEndedAtMillis,
    binsKept = binsKeptDate?.let { BinCollection(LocalDate.parse(it), decodeBins(binsKeptBins.orEmpty())) },
)

private fun ReminderState.toEntity() = ReminderStateEntity(
    binsOn = binsOn,
    binsMinuteOfDay = binsTime.hour * 60 + binsTime.minute,
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
