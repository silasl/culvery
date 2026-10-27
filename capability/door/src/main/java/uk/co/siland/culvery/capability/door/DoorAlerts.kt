package uk.co.siland.culvery.capability.door

import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.door.ui.DoorCardHost
import uk.co.siland.culvery.capability.door.ui.DoorCardUi
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.WallClock

/** A card closes by itself after this long: 60 seconds (9 §2.4). */
const val CARD_MS = 60_000L

/** A chime waits this long after the last one (5 §4.6). */
const val CHIME_GAP_MS = 10_000L

data class ShowingRing(val eventId: Long, val card: DoorCardUi)

/**
 * The alert decision (5 §4.6–4.7). A new ring, or an unrecognised alert, while Culvery is in front raises the card or
 * refreshes it (a loud alert: the shell turns music down while it shows, 6 §3.3), restarts its 60 seconds (9 §2.4), and chimes unless it is quiet hours or the last chime that played was under 10 seconds ago.
 * A re-post of the card's own ring updates the card only. Motion, and anything while Culvery isn't in front, at night (8 §2.7), or while Holiday mode is on (11 §5.5), is
 * only stored. Every change to the card happens under one lock, so a close never takes a newer ring's card.
 * quieted() is awaited inside the lock, so a Got it tap in those 500 ms waits for them.
 */
@Singleton
class DoorAlerts internal constructor(
    private val host: AlertHost,
    private val markSeen: suspend (Long) -> Unit,
    private val prefs: () -> DoorPrefs,
    private val chime: Chime,
    private val zone: suspend () -> ZoneId,
    private val clock: WallClock,
    private val scope: CoroutineScope,
    private val night: NightHours,
    private val holiday: () -> Boolean?,
) {
    @Inject
    constructor(
        host: AlertHost,
        store: DoorStore,
        settings: DoorSettings,
        chime: Chime,
        zone: HouseholdZone,
        clock: WallClock,
        @ApplicationScope scope: CoroutineScope,
        night: NightHours,
        house: HouseMode,
    ) : this(host, store::markSeen, { settings.current.value }, chime, zone::current, clock, scope, night, { house.holiday.value })

    private val lock = Mutex()
    private val _showing = MutableStateFlow<ShowingRing?>(null)
    val showing: StateFlow<ShowingRing?> = _showing.asStateFlow()
    private var closing: Job? = null
    private var lastChimeMillis: Long? = null

    suspend fun onRecorded(recorded: Recorded) = lock.withLock {
        val event = recorded.event
        if (event.kind != DoorKind.RING) return@withLock
        val z = zone()
        val card = DoorCardUi(event.unrecognised, event.device, clockTime(event.atMillis, z), event.snapshot)
        if (!recorded.isNew) {
            if (_showing.value?.eventId == event.id) _showing.value = ShowingRing(event.id, card)
            return@withLock
        }
        // Not known yet counts as night, so a ring just after a restart at night stays quiet.
        if (!host.inFront.value || night.isNight() != false || holiday() == true) return@withLock
        val alreadyShowing = _showing.value != null
        _showing.value = ShowingRing(event.id, card)
        if (!alreadyShowing) host.show(loud = true) { DoorCardHost(this@DoorAlerts) }
        closing?.cancel()
        closing = scope.launch {
            delay(CARD_MS)
            // A ring that came as the 60 seconds ended has replaced this job: only this job's card goes.
            lock.withLock { if (closing === coroutineContext[Job]) close() }
        }
        val now = clock.nowMillis()
        val p = prefs()
        // A clock set back since the last chime counts as long ago.
        val chimedRecently = lastChimeMillis?.let { now - it in 0 until CHIME_GAP_MS } == true
        if (quietAt(now, z, p) || chimedRecently) return@withLock
        // Music and the like go quieter first, for half a second at most (6 §3.3).
        host.quieted()
        if (chime.play(p.volume)) lastChimeMillis = now
    }

    /** Got it on [eventId]'s card: that ring counts as seen, and the card goes if it still shows that ring (5 §4.7). */
    fun gotIt(eventId: Long) {
        scope.launch {
            lock.withLock { closeShowing(eventId) }
            markSeen(eventId)
        }
    }

    /** Door history on [eventId]'s card: the card goes if it still shows that ring, which stays unseen for the Door tab (9 §4.3). */
    fun close(eventId: Long) {
        scope.launch { lock.withLock { closeShowing(eventId) } }
    }

    private fun closeShowing(eventId: Long) {
        if (_showing.value?.eventId == eventId) {
            closing?.cancel()
            close()
        }
    }

    private fun close() {
        _showing.value = null
        host.dismiss()
    }
}
