package uk.co.siland.culvery.capability.party

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.TURN_OFF_NEEDS_A_PIN
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.ModeRefusal

/** What a stored mode's tile does (15b §4.3): one tap, a fresh Adult or Admin PIN only where the mode needs one, and one mode at a time. */
@Singleton
class StoredModeActions @Inject internal constructor(
    private val access: AccessControl,
    private val house: StoredModesHouse,
    private val store: ModesStore,
    switches: Set<@JvmSuppressWildcards HouseModeSwitch>,
) {
    /** Holiday mode's half of one at a time; another stored mode being on is this class's own (15b Ruling 8). */
    private val holiday = ModeRefusal(house, switches)

    /** The mode whose last tap was refused, until a tap that isn't or the mode in the way goes off. */
    private val refused = MutableStateFlow<String?>(null)

    /** "Turn {mode} off first." on [modeId]'s tile while its last tap was refused and that mode is still on. */
    fun refusal(modeId: String): Flow<String?> = combine(refused, holiday.line, house.onMode, store.prefs) { refusedId, holidayLine, onId, prefs ->
        if (refusedId != modeId) return@combine null
        val line = holidayLine ?: onId?.takeIf { it != modeId }?.let { HouseLines.turnOffFirst(prefs.mode(it)?.name ?: house.modeName) }
        // Forgotten once nothing is in the way, so the next mode going on doesn't bring the line back without a tap.
        if (line == null) refused.compareAndSet(modeId, null)
        line
    }.distinctUntilChanged()

    /**
     * The tile's and the page's one action. [sawOn] is what the caller showed, so a tap on a state that has since changed
     * is ignored. Nothing is asked before the record is read, while a change runs, or when another mode is on.
     */
    suspend fun tap(modeId: String, sawOn: Boolean) {
        if (house.running.value != null || house.isOn.value == null) return
        val on = house.onMode.value == modeId
        if (sawOn != on) return
        if (!on && refuses(modeId)) return
        if (on) holiday.refusesTap(turningOn = false)
        refused.value = null
        val name = store.prefsSoon().mode(modeId)?.name ?: return
        val grant = if (house.needsPin(modeId)) {
            ModeGrant.Person(access.authorise(HousePermissions.STORED_MODE, label = ModesWords.turnLabel(name, !on)) ?: return)
        } else {
            ModeGrant.Wall(modeId)
        }
        // A change cut short by the tile leaving Home would leave some parts changed.
        withContext(NonCancellable) {
            if (on) {
                house.turnOff(grant)
            } else if (anotherStoredModeIsOn(modeId) || !holiday.turnOnUnlessRefused { house.turnOn(modeId, grant) }) {
                // One at a time again after the PIN, which another mode may have gone on behind (S2).
                refused.value = modeId
            }
        }
    }

    /** Turning [modeId]'s PIN off asks a fresh Admin PIN first, before the store is touched; false when it was refused and nothing was written. On never asks. */
    suspend fun setNeedsPin(id: String, on: Boolean): Boolean {
        if (!on && access.authorise(CorePermissions.CONNECTIONS_MANAGE, label = TURN_OFF_NEEDS_A_PIN) == null) return false
        store.setNeedsPin(id, on)
        return true
    }

    /** True, with [modeId]'s tile saying why, while another mode is on: Holiday mode, or a stored one (decision 1). */
    private fun refuses(modeId: String): Boolean {
        val blocked = anotherStoredModeIsOn(modeId) || holiday.refusesTap(turningOn = true)
        if (blocked) refused.value = modeId
        return blocked
    }

    private fun anotherStoredModeIsOn(modeId: String) = house.onMode.value.let { it != null && it != modeId }
}
