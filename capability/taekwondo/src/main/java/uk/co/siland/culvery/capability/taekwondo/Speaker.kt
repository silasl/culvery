package uk.co.siland.culvery.capability.taekwondo

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "TtsSpeaker"

interface Speaker {
    /** True once a Korean voice is known to be on the tablet. */
    val hasKorean: Boolean

    /** Returns when [text] has been spoken, or at once if it can't be. */
    suspend fun say(text: String, korean: Boolean)

    fun stop()
}

/**
 * The tablet's own speech engine. It starts when first built, which is when the practice screen first opens, so it is ready
 * well inside the 10 s countdown. Nothing installs voice data: with no Korean voice [hasKorean] stays false.
 */
@Singleton
class TtsSpeaker @Inject constructor(@ApplicationContext context: Context) : Speaker {
    @Volatile
    override var hasKorean = false
        private set

    @Volatile
    private var ready = false

    private val waiting = ConcurrentHashMap<String, () -> Unit>()
    private val utterances = AtomicLong()
    private val engine: TextToSpeech = TextToSpeech(context) { status -> started(status) }

    private fun started(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "The speech engine didn't start")
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finished(utteranceId)

            // Abstract in the SDK, so it is overridden next to onError(String, Int), which is the one that runs.
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) = finished(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        })
        hasKorean = engine.isLanguageAvailable(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
        ready = true
    }

    private fun finished(utteranceId: String?) {
        utteranceId?.let { waiting.remove(it) }?.invoke()
    }

    override suspend fun say(text: String, korean: Boolean) {
        if (!ready) return
        suspendCancellableCoroutine { continuation ->
            val id = utterances.incrementAndGet().toString()
            waiting[id] = { continuation.resume(Unit) }
            continuation.invokeOnCancellation {
                waiting.remove(id)
                engine.stop()
            }
            engine.setLanguage(if (korean) Locale.KOREAN else Locale.UK)
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finished(id)
        }
    }

    override fun stop() {
        engine.stop()
    }
}
