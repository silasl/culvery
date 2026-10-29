package uk.co.siland.culvery.core.house

import android.util.Log
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "ModePlugs"

/** A mode's plugs (12 §5.2): each throw is logged by mode and exception type only, never a plug's name. */
class ModePlugs(private val plugs: HousePlugs, private val modeId: String) {
    /** True while the mode's list switches a plug. */
    suspend fun ready(): Boolean = plugs.ready(modeId).first()

    suspend fun preview(): List<PlugChange> = plugs.preview(modeId)

    /** Takes the snapshot; one that throws leaves nothing for [enter] to switch. */
    suspend fun begin(by: Authorised) {
        attempt({ plugs.begin(modeId, by) }) { e -> Log.w(TAG, "The ${this.modeId} plugs didn't begin (${e::class.simpleName})") }
    }

    /** Switches the begun plugs: null with no snapshot, so nothing to retry; a throw is [HouseLines.PLUGS_THREW], to retry. */
    suspend fun enter(): HouseStep? = attempt({ plugs.enter(modeId) }) { e ->
        Log.w(TAG, "The ${this.modeId} plugs step threw ${e::class.simpleName}")
        HouseStep.Failed(HouseLines.PLUGS_THREW)
    }

    /** Puts the plugs back: the line for those that didn't go, or null when they all did. */
    suspend fun leave(): String? {
        val line = leaving().line(threw = HouseLines.PLUGS_LEAVE_THREW)
        if (line != null) Log.w(TAG, "The ${this.modeId} plugs didn't all go back")
        return line
    }

    /** Puts back plugs a cut-short turn-on left begun, with no line for the tile. */
    suspend fun leaveQuietly() {
        leaving()
    }

    private suspend fun leaving(): HouseStep? = attempt({ plugs.leave(modeId) }) { e ->
        Log.w(TAG, "The ${this.modeId} plugs threw ${e::class.simpleName} going back")
        null
    }
}
