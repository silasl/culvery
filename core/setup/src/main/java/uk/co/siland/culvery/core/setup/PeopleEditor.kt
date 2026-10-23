package uk.co.siland.culvery.core.setup

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.PinInUseException
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.ColourInUseException
import uk.co.siland.culvery.core.household.DuplicateNameException
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.LastAdminException
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.PinChange
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.Toaster

/** What the editor sheet's Save or Remove came to. */
sealed interface PeopleOutcome {
    data object Done : PeopleOutcome

    /** The PIN pad was cancelled, or the person may not do this; nothing changed. */
    data object Cancelled : PeopleOutcome

    /** Nothing changed; the sheet shows [message] under [field], or above its footer when null, and keeps what was typed. */
    data class Refused(val message: String, val field: PersonField? = null) : PeopleOutcome
}

/** The part of the person sheet a refusal is about. */
enum class PersonField { NAME, COLOUR, ROLE, PIN }

/** A person as the sheet has them. [newPin] is a PIN chosen in the sheet; [removePin] is Remove PIN tapped. */
data class PersonDraft(
    val name: String,
    val color: Long,
    val role: Role,
    val newPin: String? = null,
    val removePin: Boolean = false,
) {
    // The PIN stays out of every string.
    override fun toString(): String = "PersonDraft(name=$name, color=$color, role=$role, newPin=${newPin != null}, removePin=$removePin)"
}

/**
 * The people editor's rules (4a design §3.5, §3.7, §4.4, D6): renaming and recolouring take the open session
 * (`settings.manage`); adding, removing, a role change and any PIN change take a fresh PIN (`people.manage`, ruling 6).
 * An Admin always needs a PIN (ruling 8). Changing the signed-in person's role or PIN, or removing them, signs them out.
 */
@Singleton
class PeopleEditor @Inject constructor(
    private val household: HouseholdRepository,
    private val pins: PinManager,
    private val access: AccessControl,
    private val toaster: Toaster,
) {
    val members: Flow<List<Member>> = household.members

    suspend fun add(draft: PersonDraft): PeopleOutcome {
        if (draft.role == Role.ADMIN && draft.newPin == null) return PeopleOutcome.Refused(ADMIN_NEEDS_PIN, PersonField.PIN)
        access.authorise(CorePermissions.PEOPLE_MANAGE) ?: return PeopleOutcome.Cancelled
        return saving(couldNotSaveDetails(draft.name.trim().ifEmpty { null }), named = true) { pins.addPerson(draft.name, draft.color, draft.role, draft.newPin) }
    }

    suspend fun save(id: PersonId, draft: PersonDraft): PeopleOutcome {
        val before = orNull { household.member(id) } ?: return PeopleOutcome.Refused(couldNotSaveDetails(null))
        val roleChanged = draft.role != before.role
        val pinChanged = draft.newPin != null || draft.removePin
        val keepsAPin = draft.newPin != null || (before.hasPin && !draft.removePin)
        if (draft.role == Role.ADMIN && !keepsAPin) return PeopleOutcome.Refused(ADMIN_NEEDS_PIN, PersonField.PIN)
        val permission = if (roleChanged || pinChanged) CorePermissions.PEOPLE_MANAGE else CorePermissions.SETTINGS_MANAGE
        access.authorise(permission) ?: return PeopleOutcome.Cancelled
        val outcome = saving(couldNotSaveDetails(before.person.name), named = true) {
            val pin = when {
                draft.newPin != null -> pins.changeTo(draft.newPin, owner = id)
                draft.removePin -> PinChange.Remove
                else -> PinChange.Keep
            }
            household.updateMember(id, draft.name, draft.color, draft.role, pin)
        }
        if (outcome == PeopleOutcome.Done && (roleChanged || pinChanged)) lockIfSignedIn(id)
        return outcome
    }

    suspend fun remove(id: PersonId): PeopleOutcome {
        val name = orNull { household.person(id)?.name } ?: return PeopleOutcome.Refused(couldNotSaveDetails(null))
        access.authorise(CorePermissions.PEOPLE_MANAGE) ?: return PeopleOutcome.Cancelled
        val outcome = saving(couldNotSaveDetails(name)) { household.removePerson(id) }
        if (outcome == PeopleOutcome.Done) {
            lockIfSignedIn(id)
            toaster.show(removed(name))
        }
        return outcome
    }

    /** 4c design §7.1, D7: the open Settings session (settings.manage), no fresh PIN; a toast says who moved, or that it failed. */
    suspend fun move(id: PersonId, up: Boolean): PeopleOutcome {
        val name = orNull { household.person(id)?.name }
        if (name == null) {
            toaster.show(COULD_NOT_SAVE, urgent = true)
            return PeopleOutcome.Refused(COULD_NOT_SAVE)
        }
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return PeopleOutcome.Cancelled
        var moved = false
        val outcome = saving(couldNotMove(name)) { moved = household.move(id, up) }
        when {
            outcome is PeopleOutcome.Refused -> toaster.show(outcome.message, urgent = true)
            moved -> toaster.show(if (up) movedUp(name) else movedDown(name))
        }
        return outcome
    }

    /** Whether [id] is who is signed in now. */
    fun isSignedIn(id: PersonId): Boolean = access.session.value?.person?.id == id

    private fun lockIfSignedIn(id: PersonId) {
        if (isSignedIn(id)) access.lock()
    }

    /** Runs [block]; when [named], a refusal says which field it is about, else it belongs above the sheet's footer. */
    private suspend fun saving(failure: String, named: Boolean = false, block: suspend () -> Unit): PeopleOutcome {
        fun refused(message: String, field: PersonField) = PeopleOutcome.Refused(message, field.takeIf { named })
        return try {
            block()
            PeopleOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: DuplicateNameException) {
            refused(someoneCalled(e.name), PersonField.NAME)
        } catch (e: ColourInUseException) {
            refused(COLOUR_TAKEN, PersonField.COLOUR)
        } catch (e: PinInUseException) {
            refused(PIN_TAKEN, PersonField.PIN)
        } catch (e: LastAdminException) {
            refused(NEEDS_AN_ADMIN, PersonField.ROLE)
        } catch (e: Exception) {
            failed(e)
            PeopleOutcome.Refused(failure)
        }
    }

    private suspend fun <T> orNull(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e)
            null
        }

    // The type only: a message could hold a name.
    private fun failed(e: Exception) = Log.w(TAG, "Couldn't read or save a person (${e::class.simpleName})")

    private companion object {
        const val TAG = "People"
    }
}
