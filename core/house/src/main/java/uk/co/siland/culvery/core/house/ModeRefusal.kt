package uk.co.siland.culvery.core.house

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "ModeRefusal"

/**
 * The line [own]'s tile shows when another mode being on kept it from turning on (13 §2.6): "Turn {mode} off first."
 * from a refusal until the next refusal or a turn-off, and only while that mode is still on, so it goes once the mode
 * is turned off. It is read from [switches] as they change, so no scope is needed to keep it current.
 */
class ModeRefusal(private val own: HouseModeSwitch, private val switches: Set<HouseModeSwitch>) {
    private val refusedBy = MutableStateFlow<HouseModeSwitch?>(null)

    /** The line as it changes, starting with [current]. */
    val line: Flow<String?> = combine(switches.map { it.isOn } + refusedBy) { current() }.distinctUntilChanged()

    fun current(): String? = refusedBy.value?.takeIf { it.isOn.value == true }?.let { HouseLines.turnOffFirst(it.modeName) }

    /** Whether a tap is refused: turning off never is, and clears the line; turning on is while another mode is on. */
    fun refusesTap(turningOn: Boolean): Boolean {
        if (turningOn) return refuse()
        refusedBy.value = null
        return false
    }

    /** Runs [turnOn] and answers true, unless another mode went on since the tap: then it is logged and false. */
    suspend fun turnOnUnlessRefused(turnOn: suspend () -> Unit): Boolean {
        if (refuse()) {
            Log.w(TAG, "${own.modeName} wasn't turned on because another mode is on")
            return false
        }
        turnOn()
        return true
    }

    /** True when another mode is on, which the line then names; clears the line when none is. */
    private fun refuse(): Boolean {
        val other = switches.blockedBy(own.modeId)
        refusedBy.value = other
        return other != null
    }
}
