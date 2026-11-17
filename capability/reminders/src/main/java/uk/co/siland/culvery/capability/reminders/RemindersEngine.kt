package uk.co.siland.culvery.capability.reminders

import android.util.Log
import androidx.compose.runtime.Composable
import java.time.LocalDateTime
import dagger.Lazy
import java.time.ZoneId
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.reminders.ui.ReminderCardHost
import uk.co.siland.culvery.core.house.BinCollection
import uk.co.siland.culvery.core.house.BinCollections
import uk.co.siland.culvery.core.house.BinDays
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.AlertHandle
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.ScreenLit
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.sound.REMINDER_CHIME_MS
import uk.co.siland.culvery.core.sound.SoundPlayer

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
    private val bins: Flow<BinDays?>,
    private val lit: StateFlow<Boolean>,
    private val door: DoorQuiet?,
    private val chime: suspend (volume: Float) -> PadStart,
    private val scope: CoroutineScope,
    private val card: @Composable (RemindersEngine) -> Unit,
) : Startable {
    @Inject
    constructor(
        store: RemindersStore,
        host: AlertHost,
        clock: HouseholdClock,
        wall: WallClock,
        zone: HouseholdZone,
        setup: SetupState,
        house: HouseMode,
        night: NightHours,
        lit: ScreenLit,
        door: Optional<DoorQuiet>,
        bins: Optional<BinCollections>,
        player: SoundPlayer,
        view: Lazy<RemindersView>,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        store, host, clock.minutes, wall, { zone.current() }, setup.setupComplete, house.holiday, night, bins.binDays(), lit.lit, door.orElse(null),
        { volume -> player.playReminder(volume) }, scope,
        { engine -> ReminderCardHost(view.get(), engine) },
    )

    private val _waiting = MutableStateFlow<List<Due>>(emptyList())
    private val _showing = MutableStateFlow<Showing?>(null)

    /** Every waiting reminder, snoozed ones included, in order. */
    val waiting: StateFlow<List<Due>> = _waiting

    /** What the card shows; null while it is down. */
    val showing: StateFlow<Showing?> = _showing

    private val mutex = Mutex()
    private val triggers = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var setupDone = false
    @Volatile private var binDays: BinDays? = null
    private val setupKnown = CompletableDeferred<Unit>()

    // What the last pass saw, to log and wake on a change only; touched under the mutex.
    private var handle: AlertHandle? = null
    private var lastDueCount = 0
    private var lastCard: Pair<DueKey, Int>? = null
    private var lastHeld: Held? = null
    private var lastOwed = emptySet<DueKey>()
    private var lastQuiet = false
    private var lastBins: DueKey.Bins? = null

    /** When each key's chime was paid, kept here as well as stored: a mark that can't be saved must not repeat the chime every minute. */
    private val chimedAt = mutableMapOf<DueKey, Long>()

    override fun start() {
        scope.launch {
            supervisorScope {
                input(minutes) { poke() }
                input(store.reminders) { poke() }
                input(store.state) { poke() }
                input(setupComplete) { setupDone = it; setupKnown.complete(Unit); poke() }
                input(host.inFront) { poke() }
                input(host.showing) { poke() }
                input(holiday) { poke() }
                input(bins) { binDays = it; poke() }
                input(lit) { poke() }
                runPasses()
            }
        }
    }

    /** One input failing is logged and leaves the others running; each pass reads the store afresh. */
    private fun <T> CoroutineScope.input(flow: Flow<T>, onEach: (T) -> Unit) {
        launch {
            flow.catch { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "An input failed (${e::class.simpleName})")
            }.collect { onEach(it) }
        }
    }

    private suspend fun runPasses() {
        // No pass before setup's value: it would log "held away" for a wall that is only waiting to be told.
        setupKnown.await()
        for (ignored in triggers) guarded(PASS) { mutex.withLock { pass() } }
    }

    fun done(key: DueKey) = answer(key) {
        when (key) {
            is DueKey.Mine -> store.done(key.id, clock.nowMillis())
            is DueKey.Bins -> store.binsDone(key.date)
        }
        Log.i(TAG, "done")
    }

    fun later(key: DueKey) = answer(key) {
        when (key) {
            is DueKey.Mine -> store.later(key.id, clock.nowMillis())
            is DueKey.Bins -> store.binsLater(clock.nowMillis())
        }
        Log.i(TAG, "later")
    }

    /** A key no longer waiting (answered already, or gone) is ignored. */
    private fun answer(key: DueKey, write: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                guarded(PASS) {
                    if (_waiting.value.none { it.key == key }) return@guarded
                    write()
                    pass()
                }
            }
        }
    }

    private fun poke() {
        triggers.trySend(Unit)
    }

    /** Logs the exception's class as "[failedStep] failed" and carries on. */
    private suspend fun guarded(failedStep: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "$failedStep failed (${e::class.simpleName})")
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
        val binsNow = binsDue(binDays, state, now, zone)
        guarded(BINS_WRITE) { keepBinsCollection(binsNow?.second, state, now, zone) }
        val dues = inOrder(store.reminders.first().mapNotNull { it.due(now, zone) } + listOfNotNull(binsNow?.first))
        _waiting.value = dues
        if (dues.size > lastDueCount) Log.i(TAG, "due ${dues.size}")
        lastDueCount = dues.size
        logBins(binsNow?.first?.key as? DueKey.Bins, now, zone)

        val open = dues.filterNot { it.snoozed(now) }
        var live = handle?.takeIf { it.showing.value }
        val holding = heldBy(wall)
        if (open.isNotEmpty() && holding == null) {
            live = live ?: host.showIfFree(holdsAwake = false, content = { card(this) })?.also(::watch)
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
        // Only a card that was up has seen what is owed; waking is for the day.
        val quietEnded = lastQuiet && !wall.quiet && owed.isNotEmpty()
        if (live != null && wall.night == false && (quietEnded || owed.any { it !in lastOwed })) host.wake()
        lastQuiet = wall.quiet
        lastOwed = if (live != null) owed.toSet() else emptySet()
        if (live != null && owed.isNotEmpty() && soundAllowed(wall)) pay(live, owed, now)
        chimedAt.keys.retainAll(dues.mapTo(mutableSetOf()) { it.key })
    }

    /** A started bins reminder keeps its collection until Done or noon on its day, whatever the calendar lists (ruling 16). */
    private suspend fun keepBinsCollection(waiting: BinCollection?, state: ReminderState, now: Long, zone: ZoneId) {
        val kept = state.binsKept
        when {
            waiting != null -> if (waiting != kept) store.binsKept(waiting)
            kept != null && (now >= binsClearsAt(kept.date, zone) || state.binsDoneFor == kept.date) -> store.binsKept(null)
        }
    }

    private fun logBins(key: DueKey.Bins?, now: Long, zone: ZoneId) {
        val before = lastBins
        if (key != null && key != before) Log.i(TAG, "bins due")
        if (before != null && key != before && now >= binsClearsAt(before.date, zone)) Log.i(TAG, "bins cleared")
        lastBins = key
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
        const val PASS = "A pass"
        const val BINS_WRITE = "A bins write"
    }
}
