package uk.co.siland.culvery.capability.lights

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.ScreenLit
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/** A full read of the rooms and scenes, while the stream is open (7 §4.2). */
const val READ_ALL_MS = 300_000L

private const val TAG = "LightsFollow"

private class StreamEnded : Exception("The lights stream ended")

/**
 * Keeps the bridge's event stream open and reads everything every five minutes (7 §4.2), only while the screen is lit and a
 * screen that shows lights is on, which [watch] counts. The stream opens with a full read and reopens with backoff
 * when it drops.
 */
@Singleton
class LightsFollow internal constructor(
    private val system: StateFlow<LightsSystem?>,
    private val lit: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(bridges: Bridges, lit: ScreenLit, @ApplicationScope scope: CoroutineScope) : this(bridges.system, lit.lit, scope)

    private val watchers = MutableStateFlow(0)

    /** How many lights screens are on, for the screens' tests. */
    internal val watching: Int get() = watchers.value

    /** A lights screen is on; call the result when it goes. */
    fun watch(): () -> Unit {
        watchers.update { it + 1 }
        val stopped = AtomicBoolean(false)
        return { if (stopped.compareAndSet(false, true)) watchers.update { it - 1 } }
    }

    override fun start() {
        scope.launch {
            combine(system, lit, watchers) { s, isLit, n -> s.takeIf { isLit && n > 0 } }
                .distinctUntilChanged()
                .collectLatest { s ->
                    if (s != null) {
                        Log.d(TAG, "Polling on")
                        try {
                            following(s)
                        } finally {
                            Log.d(TAG, "Polling off")
                        }
                    }
                }
        }
    }

    private suspend fun following(s: LightsSystem) = coroutineScope {
        launch {
            while (true) {
                delay(READ_ALL_MS)
                attempt({ s.readAll() }) { e -> Log.w(TAG, "A full read failed (${e::class.simpleName})") }
            }
        }
        flow {
            s.readAll()
            emitAll(s.stream())
            throw StreamEnded()
        }
            .retryWithBackoff { Log.w(TAG, "The lights stream dropped (${it::class.simpleName}); reopening") }
            .collect { }
    }
}
