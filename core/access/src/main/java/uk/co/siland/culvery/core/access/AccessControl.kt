package uk.co.siland.culvery.core.access

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS

data class Authorised(val person: Person, val role: Role, val granted: Set<String>)

/** Hand-off §7: signed in for 2 minutes after the last authorised action or touch while Settings is open (4a design D5, 4d-2 §6.2). */
const val SESSION_TIMEOUT_MS = 120_000L

/** 4a design D9 (amended): the setup session ends after 10 minutes without a touch in the wizard or an authorised action. */
const val SETUP_IDLE_MS = 600_000L

/** Why the PIN pad is asking; it picks the pad's reason line. */
enum class PinReason { Generic, Save, Edit, Delete, Assign, ContinueSetup }

/** The PIN pad's reason line, from explicit copy, never an enum's name (4d-2 §7.1.2, C60). [label] is the permission's, for [PinReason.Generic]. */
fun pinReasonText(reason: PinReason, label: String): String = when (reason) {
    PinReason.Generic -> "Enter your PIN to ${label.replaceFirstChar { it.lowercase() }}."
    PinReason.ContinueSetup -> CONTINUE_SETUP
    PinReason.Save -> eventReason("add this event")
    PinReason.Edit -> eventReason("change this event")
    PinReason.Delete -> eventReason("delete this event")
    PinReason.Assign -> eventReason("choose who this event is for")
}

private fun eventReason(what: String): String = "Enter your PIN to $what. It also records who made the change."

/** 4a design §3.4: the wizard started again after a kill past its You step. */
const val CONTINUE_SETUP = "Enter your PIN to carry on setting up"

/** What happens when the identified person may not do the thing. */
sealed interface Refusal {
    /** The PIN pad says the PIN can't do this and who to ask, counts it as a wrong one, and asks again. */
    data object InPad : Refusal

    /** The PIN pad closes, [message] (given the person's name) shows as a toast, and authorise returns null. */
    class Toast(val message: (name: String) -> String) : Refusal
}

/**
 * The PIN pad's line for a real PIN that may not do this (4d-2 C64, decision A): it names no one, so a PIN found by
 * guessing doesn't say whose it is, and says who to ask.
 */
fun refusedHere(openToAdults: Boolean): String =
    if (openToAdults) "That PIN can’t do this. Ask a grown-up." else "That PIN can’t do this. Ask an Admin."

interface AccessControl {
    /** The person currently identified, or null once the session has timed out or been locked. */
    val session: StateFlow<Identified?>

    /** When the session ends, in wall-clock millis, as of its last restart; null with nobody signed in (4d-2 §6.2, the warning). */
    val expiresAt: StateFlow<Long?>

    /**
     * Succeeds if the identified person (or whoever enters a PIN) holds at least one of [anyOf] and [allow]
     * agrees. Shows the PIN pad when needed, and always for fresh-PIN permissions. Returns null if cancelled
     * or refused.
     *
     * [allow] runs on the session shortcut and after each PIN, with the permissions the person holds.
     * [refusal] decides whether a "no" asks for another PIN in the pad, or closes the pad with a toast. With
     * [Refusal.Toast], a signed-in person who is refused gets the toast without a PIN pad and is signed out, so
     * the next tap asks for a PIN. Each success restarts the session's two minutes.
     */
    suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason = PinReason.Generic,
        allow: (Identified, granted: Set<String>) -> Boolean = { _, granted -> granted.isNotEmpty() },
        refusal: Refusal = Refusal.InPad,
    ): Authorised?

    /**
     * The setup wizard only; the shell never calls it. 4a design §3.4: [person], the Admin the wizard has just made (or
     * who entered their PIN at its gate), is signed in and every authorise for them passes without a PIN (fresh-PIN
     * permissions too) until [endSetupSession], [lock], a PIN entered at a pad, or [SETUP_IDLE_MS] without a touch or an
     * authorised action. Kept in memory only, so no later start of the app inherits it.
     */
    fun beginSetupSession(person: Identified)

    /** Ends the setup session; whoever was signed in stays signed in for the usual two minutes. */
    fun endSetupSession()

    /**
     * A touch while Settings is open or the wizard shows (4a design D5, D9; 4d-2 §6.2): restarts the session's two minutes,
     * or the setup session's ten. [holdForEdits]: an open sheet has unsaved changes, so it restarts for [EDIT_HOLD_MS]
     * instead, and an authorise on the session keeps that until a touch without it. Nothing with nobody signed in, and
     * nothing for a session the wall clock has already ended.
     */
    fun touch(holdForEdits: Boolean = false)

    /** Ends the session if the wall clock has passed [expiresAt]: its timer stops while the tablet sleeps. On start and resume. */
    fun checkExpiry()

    fun lock()
}
