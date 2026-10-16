package uk.co.siland.culvery.provider.lights_hue

import android.util.Log

/**
 * Debug builds only (7 §8.2): with `adb shell setprop log.tag.HueCapture DEBUG`, each answer's shape goes to logcat,
 * names, the bridge id, the key and addresses replaced; `… INFO` turns it off. In pieces, as logcat cuts long lines.
 */
internal object HueCapture {
    private const val TAG = "HueCapture"
    private const val PIECE = 3_000

    fun log(what: String, answer: String) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        HueJson.redact(answer).chunked(PIECE).forEachIndexed { i, piece -> Log.d(TAG, "$what [$i] $piece") }
    }
}
