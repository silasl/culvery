package uk.co.siland.culvery.capability.party

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.ModeRefusal

private const val TAG = "PartyActions"

/** What the Party mode tile does (13 §2.7): a fresh Adult or Admin PIN each time, to open the sheet either way. */
@Singleton
class PartyActions @Inject internal constructor(
    private val access: AccessControl,
    private val house: PartyHouse,
    switches: Set<@JvmSuppressWildcards HouseModeSwitch>,
) {
    private val refused = ModeRefusal(house.modeId, switches)

    /** "Turn Holiday mode off first." while a tap or confirm was refused and Holiday mode is still on. */
    val refusal: StateFlow<String?> = refused

    /**
     * Calls [open] with the PIN's grant and whether the sheet turns Party on, once the PIN is right. Nothing is asked
     * before the record is read, while a change runs, or when turning on is refused because Holiday mode is on.
     */
    suspend fun toggle(open: (Authorised, Boolean) -> Unit) {
        if (house.running.value != null) return
        val on = house.isOn.value ?: return
        if (on) refused.clear() else if (refused.refuse()) return
        val by = access.authorise(HousePermissions.PARTY_MODE) ?: return
        open(by, !on)
    }

    /** False, with the refusal showing, when Holiday mode went on after the sheet opened. */
    suspend fun turnOn(by: Authorised): Boolean {
        if (refused.refuse()) {
            Log.w(TAG, "Party mode wasn't turned on because another mode is on")
            return false
        }
        house.turnOn(by)
        return true
    }

    // A turn-off cut short by the sheet closing would leave some parts changed and the record on.
    suspend fun turnOff(by: Authorised) = withContext(NonCancellable) { house.turnOff(by) }
}
