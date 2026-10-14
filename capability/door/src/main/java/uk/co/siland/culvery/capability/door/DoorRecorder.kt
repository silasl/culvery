package uk.co.siland.culvery.capability.door

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.WallClock

/**
 * The capability's [DoorEventSink] (5 §3.1, §4): records each notice in the order it arrived, keeps "last heard" and the
 * run of unrecognised alerts, then hands the record to [alert]. One queue, so a text post and its snapshot re-post
 * can't race.
 */
@Singleton
class DoorRecorder internal constructor(
    private val store: DoorStore,
    private val settings: DoorSettings,
    private val scope: CoroutineScope,
    private val clock: WallClock,
    private val alert: suspend (Recorded) -> Unit,
) : DoorEventSink {
    @Inject
    constructor(store: DoorStore, settings: DoorSettings, @ApplicationScope scope: CoroutineScope, clock: WallClock) :
        this(store, settings, scope, clock, {})

    private val notices = Channel<DoorNotice>(Channel.UNLIMITED)

    private val _bound = MutableStateFlow<Boolean?>(null)

    /** Whether Android has the listener bound; null until it first says (5 §6). */
    val bound: StateFlow<Boolean?> = _bound.asStateFlow()

    init {
        scope.launch { for (notice in notices) record(notice) }
    }

    override fun heard(atMillis: Long) {
        scope.launch {
            try {
                settings.heard(atMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't keep when Ring was last heard (${e::class.simpleName})")
            }
        }
    }

    override fun post(notice: DoorNotice) {
        notices.trySend(notice)
    }

    override fun listening(bound: Boolean) {
        _bound.value = bound
    }

    override val capturing: Boolean
        get() = settings.current.value.capturingAt(clock.nowMillis())

    private suspend fun record(notice: DoorNotice) {
        val recorded = try {
            store.record(notice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't record a door notice (${e::class.simpleName})")
            return
        }
        if (recorded.isNew) {
            try {
                settings.countAlert(recognised = !notice.unrecognised)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't count an alert (${e::class.simpleName})")
            }
        }
        try {
            alert(recorded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't alert on a door notice (${e::class.simpleName})")
        }
    }

    private companion object {
        const val TAG = "DoorRecorder"
    }
}
