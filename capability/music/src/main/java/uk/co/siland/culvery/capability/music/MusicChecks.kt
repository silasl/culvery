package uk.co.siland.culvery.capability.music

import android.util.Log
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.FailureRun
import uk.co.siland.culvery.core.plugin.ScreenLit
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.failuresPhrase
import uk.co.siland.culvery.core.plugin.polling

/** What plays, and the group volumes, while a music screen shows (6 §4.2). */
const val PLAYBACK_CHECK_MS = 2_000L

/** Rooms, groups and room volumes (6 §4.2). */
const val LAYOUT_CHECK_MS = 30_000L

/** A failing playback check is retried this often at most (19 §5). */
const val PLAYBACK_BACKOFF_CAP_MS = 60_000L

/** A failing layout read is retried this often at most (19 §5). */
const val LAYOUT_BACKOFF_CAP_MS = 300_000L

private const val TAG = "MusicChecks"
private const val NEVER = Long.MIN_VALUE

/**
 * Checks the speakers on a timer rather than subscribing (6 §4.2): only while the screen is lit and a screen that shows
 * music (Home's card, the Music tab) is on, which [watch] counts. The layout first, then playback every 2 seconds and the
 * layout every 30, or at once when the Music tab opens. A failing check waits longer each time (19 §5).
 */
@Singleton
class MusicChecks internal constructor(
    private val system: StateFlow<MusicSystem?>,
    private val lit: StateFlow<Boolean>,
    private val elapsed: () -> Long,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(speakers: Speakers, lit: ScreenLit, @ApplicationScope scope: CoroutineScope) :
        this(speakers.system, lit.lit, SystemClock::elapsedRealtime, scope)

    private val watchers = MutableStateFlow(0)
    private val layoutAsked = Channel<Unit>(Channel.CONFLATED)
    private val playbackAsked = Channel<Unit>(Channel.CONFLATED)
    private val lastLayoutAt = AtomicLong(NEVER)

    /** How many music screens are on, for the screens' tests. */
    internal val watching: Int get() = watchers.value

    /** A music screen is on; call the result when it goes. [layoutNow] for the Music tab opening (6 §4.2). */
    fun watch(layoutNow: Boolean = false): () -> Unit {
        watchers.update { it + 1 }
        if (layoutNow) layoutAsked.trySend(Unit)
        val stopped = AtomicBoolean(false)
        return { if (stopped.compareAndSet(false, true)) watchers.update { it - 1 } }
    }

    /** Reads the layout unless a read by these checks succeeded within [ageMs]; true when it read. Throws what the read throws. */
    suspend fun layoutIfOlderThan(s: MusicSystem, ageMs: Long): Boolean {
        val at = lastLayoutAt.get()
        if (at != NEVER && elapsed() - at < ageMs) return false
        readLayout(s)
        return true
    }

    override fun start() {
        scope.launch {
            combine(system, lit, watchers) { s, isLit, n -> s.takeIf { isLit && n > 0 } }
                .distinctUntilChanged()
                .collectLatest { s ->
                    if (s != null) {
                        polling(TAG) { checking(s) }
                    }
                }
        }
    }

    private suspend fun readLayout(s: MusicSystem) {
        s.checkLayout()
        lastLayoutAt.set(elapsed())
    }

    private suspend fun checking(s: MusicSystem) = coroutineScope {
        playbackAsked.tryReceive()
        launch {
            val run = FailureRun()
            playbackAsked.receive()
            while (true) {
                attempt({
                    s.checkPlayback()
                    val failedInARow = run.succeeded()
                    if (failedInARow > 0) Log.i(TAG, "Music playback checks work again after ${failuresPhrase(failedInARow)}")
                }) { e ->
                    if (run.failed()) Log.w(TAG, "A music playback check failed (${e::class.simpleName})")
                }
                val asked = withTimeoutOrNull(run.waitMillis(PLAYBACK_CHECK_MS, PLAYBACK_BACKOFF_CAP_MS)) { playbackAsked.receive() }
                if (asked != null) run.resetWaits()
            }
        }
        launch {
            val run = FailureRun()
            layoutAsked.tryReceive()
            var asked = true
            while (true) {
                if (asked) run.resetWaits()
                attempt({
                    readLayout(s)
                    val failedInARow = run.succeeded()
                    if (failedInARow > 0) Log.i(TAG, "Music layout reads work again after ${failuresPhrase(failedInARow)}")
                }) { e ->
                    if (run.failed()) Log.w(TAG, "A music layout read failed (${e::class.simpleName})")
                }
                if (asked) playbackAsked.trySend(Unit)
                asked = withTimeoutOrNull(run.waitMillis(LAYOUT_CHECK_MS, LAYOUT_BACKOFF_CAP_MS)) { layoutAsked.receive() } != null
            }
        }
    }
}
