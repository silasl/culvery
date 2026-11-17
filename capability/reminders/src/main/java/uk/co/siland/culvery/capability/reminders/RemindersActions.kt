package uk.co.siland.culvery.capability.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.today

const val PICK_A_LATER_TIME = "Pick a time that hasn’t passed."
const val LIST_FULL = "The list is full. Delete a reminder to add another."
const val REMINDER_DELETED = "Reminder deleted."
const val COULD_NOT_SAVE = "Couldn’t save — try again."
const val COULD_NOT_DELETE = "Couldn’t delete — try again."
const val COULD_NOT_OPEN = "Couldn’t open the reminder — try again."
internal const val ADD_A_REMINDER = "Add a reminder"
internal const val CHANGE_THIS_REMINDER = "Change this reminder"
internal const val CHANGE_THE_BINS_REMINDER = "Change the bins reminder"

/** Past, Full and Invalid are the sheet's to say or ignore; a refusal has already been toasted. */
enum class SaveResult { Saved, Cancelled, Past, Full, Gone, Invalid }

/** BadTime is the sheet's to say; a cancelled PIN pad is not. */
enum class BinsSave { Saved, Cancelled, BadTime }

private val ADD_PERMISSIONS = arrayOf(RemindersPermissions.ADD, RemindersPermissions.ADD_SELF)
private val CHANGE_PERMISSIONS = arrayOf(RemindersPermissions.CHANGE, RemindersPermissions.CHANGE_OWN)

/**
 * Adds, changes and deletes reminders (22 §4.6), asking who's there at the write, not when the form opens. The rule is
 * checked here, after the PIN, whatever the form showed. Nothing here ends a session (ruling 15).
 */
@Singleton
class RemindersActions @Inject constructor(
    private val access: AccessControl,
    private val store: RemindersStore,
    private val clock: WallClock,
    private val zone: HouseholdZone,
    private val toaster: Toaster,
    private val night: NightHours,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** The child whose PIN is still in force, who makes a form theirs from the start. */
    fun childHere(): PersonId? = access.session.value?.takeIf { it.role == Role.CHILD }?.person?.id

    /** Now, in the household's zone: the form's today and its first time. */
    internal suspend fun openedAt(): ZonedDateTime = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone.current())

    /** The form couldn't load (a store failure): nothing opens, and this says so. */
    internal fun couldNotOpen() = toaster.show(COULD_NOT_OPEN, urgent = true)

    /** A signed-in child tapped someone else's tile. */
    internal fun refuseOtherWho(name: String) = toaster.show(cannotAddForOthers(name), urgent = true)

    /** The reminder to show in the form, with no PIN pad. Null when it has gone, or, with a toast, when [childHere] may not change it. */
    suspend fun openExisting(id: Long): Reminder? {
        val reminder = find(id) ?: return null
        val session = access.session.value?.takeIf { it.role == Role.CHILD } ?: return reminder
        if (reminder.madeBy == session.person.id) return reminder
        toaster.show(cannotChangeOthers(session.person.name), urgent = true)
        return null
    }

    /**
     * Adds [draft], or changes reminder [editing] to it, and says how it went. A draft the form would not let through is
     * [SaveResult.Invalid] before any PIN pad. A new one needs `reminders.add`, or `add.self` for themselves alone; a change
     * needs `change`, or `change.own` on one they made, and the add rule again when who it is for changed. A one-off whose
     * time has passed, or whose day is before today, is [SaveResult.Past] when new or when its time or day changed, so a waiting one's words can be corrected.
     * [SaveResult.Cancelled]: the PIN pad was cancelled, or the person was refused and told so by a toast.
     */
    suspend fun save(editing: Long?, draft: ReminderDraft): SaveResult {
        val existing = editing?.let { find(it) ?: return SaveResult.Gone }
        val tidy = tidyWords(draft.words)
        if (!acceptable(draft, tidy, clock.today(zone.current()), existing)) return SaveResult.Invalid
        val who = (if (existing == null) authoriseAdd(draft) else authoriseChange(existing, draft)) ?: return SaveResult.Cancelled
        val tidied = draft.copy(words = tidy)
        return scope.async { write(editing, existing, tidied, who.person.id) }.await()
    }

    /** Deletes [id] and says so, once a PIN has made the change. True once it is gone, one already gone included. */
    suspend fun delete(id: Long): Boolean {
        val existing = find(id) ?: return true
        authoriseChange(existing, draft = null) ?: return false
        return scope.async {
            if (store.delete(id)) toaster.show(REMINDER_DELETED)
            true
        }.await()
    }

    /** Saves the bins reminder's switch and time once an adult has said so; a time outside midday to night hours' start is [BinsSave.BadTime] before any PIN pad. */
    suspend fun saveBins(on: Boolean, time: LocalTime): BinsSave {
        if (!binsTimeAllowed(time, night.startsAt())) return BinsSave.BadTime
        access.authorise(RemindersPermissions.BINS, label = CHANGE_THE_BINS_REMINDER) ?: return BinsSave.Cancelled
        scope.async { store.setBins(on, time) }.await()
        return BinsSave.Saved
    }

    private suspend fun find(id: Long): Reminder? = store.reminders.first().firstOrNull { it.id == id }

    /** A day more than a year ahead is refused; one before today (a form left open past midnight) is [SaveResult.Past], and a waiting one-off keeps the day it has. */
    private fun acceptable(draft: ReminderDraft, tidy: String, today: LocalDate, existing: Reminder?): Boolean {
        if (tidy.length !in 1..WORDS_MAX || draft.forPeople.isEmpty()) return false
        val once = draft.schedule as? Schedule.Once ?: return true
        val kept = (existing?.schedule as? Schedule.Once)?.date == once.date
        return kept || once.date <= today.plusYears(1)
    }

    private suspend fun authoriseAdd(draft: ReminderDraft): Authorised? = access.authorise(
        *ADD_PERMISSIONS,
        label = ADD_A_REMINDER,
        allow = { who, granted -> mayAddFor(granted, who, draft.forPeople) },
        refusal = Refusal.Toast(::cannotAddForOthers),
    )

    /** A change of who it is for follows the add rule as well (a null [draft] is a delete). */
    private suspend fun authoriseChange(existing: Reminder, draft: ReminderDraft?): Authorised? {
        val retaggedTo = draft?.forPeople?.takeIf { it.toSet() != existing.forPeople.toSet() }
        var refusedWho = false
        return access.authorise(
            *(if (retaggedTo != null) CHANGE_PERMISSIONS + ADD_PERMISSIONS else CHANGE_PERMISSIONS),
            label = CHANGE_THIS_REMINDER,
            allow = { who, granted ->
                val mayEdit = mayChange(granted, who, existing.madeBy)
                refusedWho = mayEdit && retaggedTo != null && !mayAddFor(granted, who, retaggedTo)
                mayEdit && !refusedWho
            },
            refusal = Refusal.Toast { name -> if (refusedWho) cannotAddForOthers(name) else cannotChangeOthers(name) },
        )
    }

    /** Reads the clock after the PIN pad has closed, so a time that passed while it was up is caught. */
    private suspend fun write(editing: Long?, existing: Reminder?, draft: ReminderDraft, madeBy: PersonId): SaveResult {
        val now = clock.nowMillis()
        val once = draft.schedule as? Schedule.Once
        val moved = existing == null || draft.time != existing.time || draft.schedule != existing.schedule
        if (moved && once != null && timeOn(once.date, draft.time, zone.current()) <= now) return SaveResult.Past
        if (editing == null) {
            store.add(draft, madeBy, now) ?: return SaveResult.Full
        } else if (!store.change(editing, draft, now)) {
            return SaveResult.Gone
        }
        return SaveResult.Saved
    }
}
