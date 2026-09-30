package uk.co.siland.culvery.core.access

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role

data class Authorised(val person: Person, val role: Role, val granted: Set<String>)

/** Hand-off §7: signed in for 2 minutes after the last authorised action, or the last touch while Settings is open (4a design D5). */
const val SESSION_TIMEOUT_MS = 120_000L

/** 4a design D9 (amended): the setup session ends after 10 minutes without a touch in the wizard or an authorised action. */
const val SETUP_IDLE_MS = 600_000L

/** Why the PIN pad is asking; it picks the pad's reason line. */
enum class PinReason { Generic, Save, Edit, Delete, Assign, ContinueSetup }

/** The PIN pad's reason line. [label] is the permission's label, used for [PinReason.Generic]. */
fun pinReasonText(reason: PinReason, label: String): String = when (reason) {
    PinReason.Generic -> "Enter your PIN to ${label.replaceFirstChar { it.lowercase() }}."
    PinReason.ContinueSetup -> CONTINUE_SETUP
    else -> "Enter your PIN to ${reason.name.lowercase()} this event. It also records who made the change."
}

/** 4a design §3.4: the wizard started again after a kill past its You step. */
const val CONTINUE_SETUP = "Enter your PIN to carry on setting up"

/** What happens when the identified person may not do the thing. */
sealed interface Refusal {
    /** The PIN pad says "{name} can't do that" and asks again. */
    data object InPad : Refusal

    /** The PIN pad closes, [message] (given the person's name) shows as a toast, and authorise returns null. */
    class Toast(val message: (name: String) -> String) : Refusal
}

interface AccessControl {
    /** The person currently identified, or null once the session has timed out or been locked. */
    val session: StateFlow<Identified?>

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
     * A touch while Settings is open or the wizard shows (4a design D5, D9): restarts the session's two minutes, or the
     * setup session's ten. Nothing with nobody signed in.
     */
    fun touch()

    fun lock()
}
