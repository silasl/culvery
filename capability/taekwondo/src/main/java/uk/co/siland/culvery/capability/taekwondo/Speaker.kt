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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "TtsSpeaker"

interface Speaker {
    /** Whether the tablet has a Korean voice; null until the speech engine has started. */
    val korean: StateFlow<Boolean?>

    /** Looks again for a Korean voice, which may have been installed since the engine started. */
    fun checkKorean()

    /** Returns when [text] has been spoken, or at once if it can't be. */
    suspend fun say(text: String, korean: Boolean)

    fun stop()
}

/**
 * The tablet's own speech engine. It starts when first built, which is when the practice screen first opens, so it is ready
 * well inside the 10 s countdown. Nothing installs voice data: with no Korean voice [korean] is false.
 */
@Singleton
class TtsSpeaker @Inject constructor(@ApplicationContext context: Context) : Speaker {
    override val korean = MutableStateFlow<Boolean?>(null)

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

            // Reported when the engine is stopped or flushed elsewhere, so that say() doesn't wait for a done that never comes.
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        ready = true
        checkKorean()
    }

    override fun checkKorean() {
        if (ready && korean.value != true) korean.value = engine.isLanguageAvailable(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
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
