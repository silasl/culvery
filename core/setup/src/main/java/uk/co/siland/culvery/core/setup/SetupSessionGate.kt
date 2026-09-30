package uk.co.siland.culvery.core.setup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HouseholdRepository

/**
 * 4a design §3.4, D9: the setup session lives in memory only and lapses after 10 idle minutes, so whenever the wizard
 * has an Admin but nobody signed in, it asks for an Admin's PIN, then begins the setup session again.
 */
@Singleton
class SetupSessionGate @Inject constructor(
    household: HouseholdRepository,
    private val access: AccessControl,
) {
    private val finishing = MutableStateFlow(false)

    /** An active Admin, nobody signed in, and Done not finishing (ruling 11). */
    val needsPin: Flow<Boolean> =
        combine(household.hasActiveAdmin, access.session, finishing) { admin, session, done -> admin && session == null && !done }
            .distinctUntilChanged()

    /** Done is ending setup: its sign-out must not bring the gate back. Set before it signs out. */
    fun finish() {
        finishing.value = true
    }

    /** Done couldn't finish, so setup goes on and the gate returns once nobody is signed in. */
    fun finishFailed() {
        finishing.value = false
    }

    /** The PIN pad; an Admin's PIN begins the setup session again. False when cancelled or refused. */
    suspend fun carryOn(): Boolean {
        val who = access.authorise(CorePermissions.SETTINGS_MANAGE, reason = PinReason.ContinueSetup) ?: return false
        access.beginSetupSession(Identified(who.person, who.role))
        return true
    }
}
