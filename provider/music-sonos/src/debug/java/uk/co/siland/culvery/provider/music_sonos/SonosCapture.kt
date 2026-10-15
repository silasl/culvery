package uk.co.siland.culvery.provider.music_sonos

import android.util.Log

/**
 * Debug builds only (6 §8.2): with `adb shell setprop log.tag.SonosCapture DEBUG`, each answer's shape goes to logcat,
 * names, titles, links, ids and addresses replaced; `… INFO` turns it off. In pieces, as logcat cuts long lines.
 */
internal object SonosCapture {
    private const val TAG = "SonosCapture"
    private const val PIECE = 3_000

    fun log(action: String, answer: String) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        SonosXml.redact(answer).chunked(PIECE).forEachIndexed { i, piece -> Log.d(TAG, "$action [$i] $piece") }
    }
}
