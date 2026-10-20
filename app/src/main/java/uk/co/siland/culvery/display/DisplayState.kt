package uk.co.siland.culvery.display

import android.util.Log
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.UserActivity
import uk.co.siland.culvery.core.plugin.inDailyWindow
import uk.co.siland.culvery.shell.ShellAlerts

private const val TAG = "DisplayState"
private const val MS_PER_MINUTE = 60_000L

/** The wall's three states (8 §4.1). */
enum class Screen { AWAKE, ASLEEP, NIGHT_CLOCK }

/** How long a tap or a hold's end keeps the wall awake at night (8 §4.1). */
const val NIGHT_AWAKE_MS = 60_000L

/** Null while the clock's zone or the saved night times aren't known yet. */
internal fun nightAt(now: LocalDateTime?, prefs: DisplayPrefs?): Boolean? =
    if (now == null || prefs == null) null else inDailyWindow(now.toLocalTime(), prefs.nightFrom, prefs.nightUntil)

/** What the state machine decides from: the screen, the hold, the inputs as last seen, and whether it was ever in front. */
internal data class Display(
    val screen: Screen,
    val held: Boolean,
    val inFront: Boolean,
    val night: Boolean?,
    val prefs: DisplayPrefs?,
    val seenFront: Boolean,
)

/** The idle wait running, if any: after [ms] with no activity the screen becomes [then]. */
internal data class Wait(val ms: Long, val then: Screen)

/** Before anything is known and before the screen first resumes. */
internal val START = Display(Screen.AWAKE, held = false, inFront = false, night = null, prefs = null, seenFront = false)

/**
 * [display] with new inputs. Night starting, or the first arrival in front at night, shows the clock unless held; a
 * later return from other apps, leaving the front, or night ending, wakes.
 */
internal fun next(display: Display, prefs: DisplayPrefs?, now: LocalDateTime?, inFront: Boolean): Display {
    val night = nightAt(now, prefs)
    val starts = night == true && (display.night != true || !display.seenFront)
    val screen = when {
        !inFront || night == null -> Screen.AWAKE
        !display.inFront && display.seenFront -> Screen.AWAKE
        starts -> if (display.held) Screen.AWAKE else Screen.NIGHT_CLOCK
        night == false && display.night == true -> Screen.AWAKE
        else -> display.screen
    }
    return display.copy(screen = screen, inFront = inFront, night = night, prefs = prefs, seenFront = display.seenFront || inFront)
}

internal fun Display.waiting(): Wait? = when {
    screen != Screen.AWAKE || !inFront || held || night == null || prefs == null -> null
    night -> Wait(NIGHT_AWAKE_MS, Screen.NIGHT_CLOCK)
    else -> Wait(prefs.sleepAfterMinutes * MS_PER_MINUTE, Screen.ASLEEP)
}

/** Wanted only by day, in front, off the night clock, and when allowed and switched on (8 §4.3). */
internal fun Display.wantsCamera(camera: CameraState): Boolean =
    inFront && night == false && screen != Screen.NIGHT_CLOCK && prefs?.wakesByCamera(camera) == true

/**
 * Whether the wall is awake, asleep or showing the night clock (8 §4.1), and whether the camera may look (8 §4.3).
 * [touches] is a running count whose first value is the count so far; each later value restarts the wait while awake.
 */
@Singleton
class DisplayState internal constructor(
    private val prefs: StateFlow<DisplayPrefs?>,
    private val now: StateFlow<LocalDateTime?>,
    touches: Flow<Long>,
    inFront: StateFlow<Boolean>,
    camera: StateFlow<CameraState>,
    scope: CoroutineScope,
) : NightHours {
    @Inject
    constructor(
        settings: DisplaySettings,
        clock: HouseholdClock,
        activity: UserActivity,
        alerts: ShellAlerts,
        camera: CameraAccess,
        @ApplicationScope scope: CoroutineScope,
    ) : this(settings.read, clock.now, activity.touches, alerts.shown, camera.state, scope)

    private val lock = Any()
    private val state = MutableStateFlow(next(START, prefs.value, now.value, inFront.value))
    private val activity = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val screen: StateFlow<Screen> = state.map { it.screen }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, state.value.screen)

    /** Whether it is night as the state machine last saw it; null while not known yet. */
    val night: StateFlow<Boolean?> = state.map { it.night }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, state.value.night)

    val cameraWanted: StateFlow<Boolean> = combine(state, camera) { display, access -> display.wantsCamera(access) }
        .stateIn(scope, SharingStarted.Eagerly, state.value.wantsCamera(camera.value))

    init {
        scope.launch {
            combine(prefs, now, inFront, ::Triple).collect { (p, n, f) -> change { next(it, p, n, f) } }
        }
        scope.launch {
            touches.drop(1).collect { if (state.value.screen == Screen.AWAKE) activity.tryEmit(Unit) }
        }
        scope.launch {
            state.map { it.waiting() }.distinctUntilChanged().collectLatest { wait ->
                if (wait == null) return@collectLatest
                activity.onSubscription { emit(Unit) }.collectLatest {
                    delay(wait.ms)
                    change { if (it.waiting() == wait) it.copy(screen = wait.then) else it }
                }
            }
        }
    }

    override fun isNight(): Boolean? = nightAt(now.value, prefs.value)

    /** The sleep layer's own tap or TalkBack click: wakes the dark screen and the night clock. */
    fun tapped() = wake { true }

    /** Someone moved in front of the camera: wakes the dark screen by day, never the night clock. */
    fun moved() = wake { it.night == false && it.screen != Screen.NIGHT_CLOCK }

    /** Something on screen keeps the wall awake while [on], waking it as it starts; the wait starts again as it ends. */
    fun held(on: Boolean) = change { it.copy(screen = if (on) Screen.AWAKE else it.screen, held = on) }

    private fun wake(allowed: (Display) -> Boolean) = change {
        when {
            !it.inFront || !allowed(it) -> it
            it.screen == Screen.AWAKE -> it.also { activity.tryEmit(Unit) }
            else -> it.copy(screen = Screen.AWAKE)
        }
    }

    private fun change(step: (Display) -> Display) {
        synchronized(lock) {
            val before = state.value
            val after = step(before)
            state.value = after
            // A wait the collector never saw end (a hold on and off between its runs) is restarted here.
            if (after.waiting() != before.waiting()) activity.tryEmit(Unit)
            if (after.screen != before.screen) Log.i(TAG, "Screen ${after.screen.name}")
        }
    }
}
