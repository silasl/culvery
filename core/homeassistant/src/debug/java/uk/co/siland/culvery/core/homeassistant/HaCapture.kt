package uk.co.siland.culvery.core.homeassistant

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug builds only (11 §8.2): with `adb shell setprop log.tag.HaCapture DEBUG`, each answer's shape goes to logcat
 * with names, ids, addresses and the token replaced; `... INFO` turns it off. In pieces, as logcat cuts long lines.
 */
internal object HaCapture {
    private const val TAG = "HaCapture"
    private const val PIECE = 3_000

    /** The power and energy sensors seen so far, whose readings stay blank; forgotten when dropped from [keep] (12 §6). */
    private val blanked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The entity ids the capture may show, set to the ones currently watched. */
    @Volatile
    var keep: Set<String> = emptySet()
        set(value) {
            if (value == field) return
            blanked.retainAll(value)
            field = value
        }

    fun log(what: HaCall, text: String) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        HaJson.redact(text, keep, blanked).chunked(PIECE).forEachIndexed { i, piece -> Log.d(TAG, "${what.label} [$i] $piece") }
    }
}
