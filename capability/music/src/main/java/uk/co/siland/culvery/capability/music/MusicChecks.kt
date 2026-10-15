package uk.co.siland.culvery.capability.music

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

/** What plays, and the group volumes, while a music screen shows (6 §4.2). */
const val PLAYBACK_CHECK_MS = 2_000L

/** Rooms, groups and room volumes (6 §4.2). */
const val LAYOUT_CHECK_MS = 30_000L

private const val TAG = "MusicChecks"

/**
 * Checks the speakers on a timer rather than subscribing (6 §4.2): only while Culvery is in front and a screen that shows
 * music (Home's card, the Music tab) is on, which [watch] counts. The layout first, then playback every 2 seconds and the
 * layout every 30, or at once when the Music tab opens.
 */
@Singleton
class MusicChecks internal constructor(
    private val system: StateFlow<MusicSystem?>,
    private val inFront: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(speakers: Speakers, host: AlertHost, @ApplicationScope scope: CoroutineScope) : this(speakers.system, host.inFront, scope)

    private val watchers = MutableStateFlow(0)
    private val layoutAsked = Channel<Unit>(Channel.CONFLATED)

    /** How many music screens are on, for the screens' tests. */
    internal val watching: Int get() = watchers.value

    /** A music screen is on; call the result when it goes. [layoutNow] for the Music tab opening (6 §4.2). */
    fun watch(layoutNow: Boolean = false): () -> Unit {
        watchers.update { it + 1 }
        if (layoutNow) layoutAsked.trySend(Unit)
        val stopped = AtomicBoolean(false)
        return { if (stopped.compareAndSet(false, true)) watchers.update { it - 1 } }
    }

    override fun start() {
        scope.launch {
            combine(system, inFront, watchers) { s, front, n -> s.takeIf { front && n > 0 } }
                .distinctUntilChanged()
                .collectLatest { s -> if (s != null) checking(s) }
        }
    }

    private suspend fun checking(s: MusicSystem) = coroutineScope {
        safely { s.checkLayout() }
        layoutAsked.tryReceive()
        launch {
            while (true) {
                safely { s.checkPlayback() }
                delay(PLAYBACK_CHECK_MS)
            }
        }
        launch {
            while (true) {
                withTimeoutOrNull(LAYOUT_CHECK_MS) { layoutAsked.receive() }
                safely { s.checkLayout() }
            }
        }
    }

    /** A failed check shows in the system's problem; the next one tries again. */
    private suspend fun safely(check: suspend () -> Unit) {
        try {
            check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A music check failed (${e::class.simpleName})")
        }
    }
}
