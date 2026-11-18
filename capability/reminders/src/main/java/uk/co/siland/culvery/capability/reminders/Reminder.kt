package uk.co.siland.culvery.capability.reminders

import java.time.LocalDate
import java.time.LocalTime
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.household.PersonId

const val WORDS_MAX = 60
const val REMINDERS_MAX = 50
const val LATER_MS = 1_800_000L

/** A first day, and for a repeat its rule from that day (22 §10.3). [toString] prints the kind only. */
sealed interface Schedule {
    val first: LocalDate

    data class Once(override val first: LocalDate) : Schedule {
        override fun toString() = "Schedule.Once"
    }

    /** [rule] is an RRULE line with no first-date line. */
    data class Repeating(override val first: LocalDate, val rule: String) : Schedule {
        override fun toString() = "Schedule.Repeating"
    }
}

/** The event a reminder was added from (22 §10.5). [toString] prints nothing. */
data class EventLink(val key: String, val day: LocalDate?, val time: LocalTime?, val rule: String?, val followsRepeat: Boolean) {
    override fun toString() = "EventLink"
}

/** What the form hands the store. [toString] leaves out the words, the people, the time and the event. */
data class ReminderDraft(
    val words: String,
    val forPeople: List<PersonId>,
    val time: LocalTime,
    val schedule: Schedule,
    val event: EventLink? = null,
) {
    override fun toString() = "ReminderDraft"
}

/** [forPeople] is person ids, [PersonId.FAMILY] alone for Family. [toString] leaves out all but the id. */
data class Reminder(
    val id: Long,
    val words: String,
    val forPeople: List<PersonId>,
    val madeBy: PersonId,
    val time: LocalTime,
    val schedule: Schedule,
    val answeredAtMillis: Long,
    val snoozedUntilMillis: Long?,
    val chimedAtMillis: Long?,
    val event: EventLink? = null,
) {
    override fun toString() = "Reminder($id)"
}

/** The bins reminder's settings and marks, and what the engine saw of night and Holiday. [binsKept] is the collection a started bins reminder is for. */
data class ReminderState(
    val binsOn: Boolean = true,
    val binsTime: LocalTime = LocalTime.of(18, 0),
    val binsDoneFor: LocalDate? = null,
    val binsSnoozedUntilMillis: Long? = null,
    val binsChimedAtMillis: Long? = null,
    val heldSeen: Boolean = false,
    val heldEndedAtMillis: Long? = null,
    val binsKept: BinCollection? = null,
) {
    override fun toString() = "ReminderState"
}

/** Line breaks and tabs as spaces, runs of spaces as one, trimmed. */
fun tidyWords(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()
