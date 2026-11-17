package uk.co.siland.culvery.capability.reminders

import android.util.Log
import androidx.compose.runtime.Composable
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.plugin.AlertHandle
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.sound.REMINDER_CHIME_MS

/**
 * Decides each minute, and at once when an input changes, whether the reminder card is up and whether a chime is owed (22 §4.4).
 * Every pass, and [done] and [later], run under one mutex.
 */
@Singleton
class RemindersEngine internal constructor(
    private val store: RemindersStore,
    private val host: AlertHost,
    private val minutes: Flow<LocalDateTime>,
    private val clock: WallClock,
    private val zone: suspend () -> ZoneId,
    private val setupComplete: Flow<Boolean>,
    private val holiday: StateFlow<Boolean?>,
    private val night: NightHours,
    private val lit: StateFlow<Boolean>,
    private val door: DoorQuiet?,
    private val chime: suspend (volume: Float) -> PadStart,
    private val scope: CoroutineScope,
    private val card: @Composable () -> Unit,
) : Startable {
    private val _waiting = MutableStateFlow<List<Due>>(emptyList())
    private val _showing = MutableStateFlow<Showing?>(null)

    /** Every waiting reminder, snoozed ones included, in order. */
    val waiting: StateFlow<List<Due>> = _waiting

    /** What the card shows; null while it is down. */
    val showing: StateFlow<Showing?> = _showing

    private val mutex = Mutex()
    private val triggers = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var setupDone = false

    // What the last pass saw, to log and wake on a change only; touched under the mutex.
    private var handle: AlertHandle? = null
    private var lastDueCount = 0
    private var lastCard: Pair<DueKey, Int>? = null
    private var lastHeld: Held? = null
    private var lastOwed = emptySet<DueKey>()

    /** When each key's chime was paid, kept here as well as stored: a mark that can't be saved must not repeat the chime every minute. */
    private val chimedAt = mutableMapOf<DueKey, Long>()

    override fun start() {
        scope.launch {
            launch { minutes.collect { poke() } }
            launch { store.reminders.collect { poke() } }
            launch { store.state.collect { poke() } }
            launch { setupComplete.collect { setupDone = it; poke() } }
            launch { host.inFront.collect { poke() } }
            launch { host.showing.collect { poke() } }
            launch { holiday.collect { poke() } }
            launch { lit.collect { poke() } }
            for (ignored in triggers) guarded { mutex.withLock { pass() } }
        }
    }

    fun done(key: DueKey) = answer(key) { id ->
        store.done(id, clock.nowMillis())
        Log.i(TAG, "done")
    }

    fun later(key: DueKey) = answer(key) { id ->
        store.later(id, clock.nowMillis())
        Log.i(TAG, "later")
    }

    /** A bins key is never waiting yet, so it is ignored here. */
    private fun answer(key: DueKey, write: suspend (id: Long) -> Unit) {
        scope.launch {
            mutex.withLock {
                guarded {
                    if (key !is DueKey.Mine || _waiting.value.none { it.key == key }) return@guarded
                    write(key.id)
                    pass()
                }
            }
        }
    }

    private fun poke() {
        triggers.trySend(Unit)
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A pass failed (${e::class.simpleName})")
        }
    }

    private suspend fun pass() {
        val now = clock.nowMillis()
        val wall = Wall(
            ready = setupDone && host.inFront.value,
            holiday = holiday.value,
            night = night.isNight(),
            lit = lit.value,
            quiet = door?.quietNow() ?: false,
        )
        if (wall.night != null && wall.holiday != null) store.markHeld(wall.night || wall.holiday, now)
        val state = store.state.first()
        val zone = zone()
        val dues = inOrder(store.reminders.first().mapNotNull { it.due(now, zone) })
        _waiting.value = dues
        if (dues.size > lastDueCount) Log.i(TAG, "due ${dues.size}")
        lastDueCount = dues.size

        val open = dues.filterNot { it.snoozed(now) }
        var live = handle?.takeIf { it.showing.value }
        val holding = heldBy(wall)
        if (open.isNotEmpty() && holding == null) {
            live = live ?: host.showIfFree(holdsAwake = false, content = card)?.also(::watch)
        } else if (live != null) {
            live.dismiss()
            live = null
        }
        handle = live
        val why = if (open.isEmpty()) null else holding ?: Held.RING.takeIf { live == null }
        _showing.value = live?.let { Showing(open.first(), open.size) }
        val shown = _showing.value?.let { it.due.key to it.of }
        if (shown != null && shown != lastCard) Log.i(TAG, "card up 1 of ${open.size}")
        lastCard = shown
        why?.let { held -> if (held != lastHeld) Log.i(TAG, "held ${held.word}") }
        lastHeld = why

        val owed = open.filter { it.withMark().chimeOwed(now, state.heldEndedAtMillis) }.map { it.key }
        if (live != null && owed.any { it !in lastOwed }) host.wake()
        lastOwed = owed.toSet()
        if (live != null && owed.isNotEmpty() && soundAllowed(wall)) pay(live, owed, now)
    }

    /** Follows a new handle: when a ring takes the layer from it, that is a reason to run a pass. */
    private fun watch(handle: AlertHandle) {
        scope.launch {
            handle.showing.first { !it }
            poke()
        }
    }

    private fun Due.withMark() = copy(chimedAtMillis = listOfNotNull(chimedAtMillis, chimedAt[key]).maxOrNull())

    private suspend fun pay(live: AlertHandle, owed: List<DueKey>, now: Long) {
        host.wake()
        host.duck(REMINDER_CHIME_MS)
        if (!live.showing.value) return
        val played = chime(door?.volume ?: 1f)
        if (played != PadStart.STARTED && played != PadStart.FAILED) return
        owed.forEach { chimedAt[it] = now }
        if (played == PadStart.STARTED) Log.i(TAG, "chime")
        store.chimed(owed.mapNotNull { (it as? DueKey.Mine)?.id }, bins = owed.any { it is DueKey.Bins }, nowMillis = now)
    }

    private companion object {
        const val TAG = "Reminders"
    }
}
