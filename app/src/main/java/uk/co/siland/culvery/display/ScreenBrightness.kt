package uk.co.siland.culvery.display

import android.view.Window
import android.view.WindowManager
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.shell.SunToday
import uk.co.siland.culvery.shell.ThemeSchedule

/** The lowest the window can be while Culvery is in front; not off (8 §4.1). */
const val LOWEST = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF

/** No level of Culvery's own: Android's brightness setting applies. */
const val ANDROIDS = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

const val FADE_MS = 60_000L
const val FADE_STEP_MS = 2_000L

private const val PERCENT = 100f

/** From sunset to sunrise; never without usable sun times, so the daytime level applies (8 §4.4). */
fun isEvening(now: LocalTime, sun: SunTimes?): Boolean =
    sun != null && sun.sunrise < sun.sunset && ThemeSchedule.isDark(now, sun)

/** The window level for these inputs; awake at night is the evening level, as a tap on the night clock wakes softly (8 §2.8). */
fun targetBrightness(screen: Screen, inFront: Boolean, night: Boolean, evening: Boolean, prefs: DisplayPrefs): Float = when {
    !inFront -> ANDROIDS
    screen != Screen.AWAKE -> LOWEST
    night || evening -> prefs.eveningPercent / PERCENT
    else -> prefs.dayPercent / PERCENT
}

/** Sets the level on Culvery's own window only; Android's brightness setting is never touched (8 §4.4). */
fun applyBrightness(window: Window, level: Float) {
    // Setting the attributes lays the window out again, so an unchanged level isn't set.
    if (window.attributes.screenBrightness == level) return
    window.attributes = window.attributes.apply { screenBrightness = level }
}

private data class Inputs(val screen: Screen, val inFront: Boolean, val night: Boolean, val evening: Boolean, val prefs: DisplayPrefs) {
    val target: Float get() = targetBrightness(screen, inFront, night, evening, prefs)
}

private class Step(val inputs: Inputs, val fades: Boolean)

/** Culvery's window brightness (8 §4.4): the switch at sunrise and sunset fades over [FADE_MS]; every other change is at once. */
@Singleton
class ScreenBrightness internal constructor(
    screen: StateFlow<Screen>,
    night: () -> Boolean?,
    inFront: StateFlow<Boolean>,
    now: Flow<LocalDateTime?>,
    sun: Flow<SunTimes?>,
    prefs: StateFlow<DisplayPrefs>,
    scope: CoroutineScope,
) {
    @Inject
    constructor(
        display: DisplayState,
        alerts: AlertHost,
        clock: HouseholdClock,
        sunToday: SunToday,
        settings: DisplaySettings,
        @ApplicationScope scope: CoroutineScope,
    ) : this(display.screen, display::isNight, alerts.inFront, clock.now, sunToday.times, settings.current, scope)

    private val _level = MutableStateFlow(ANDROIDS)
    val level: StateFlow<Float> = _level.asStateFlow()

    init {
        val inputs = combine(screen, inFront, now, sun, prefs) { s, front, time, sunTimes, p ->
            // Not known yet counts as night, so a restart in the night doesn't dazzle.
            Inputs(s, front, night() ?: true, time != null && isEvening(time.toLocalTime(), sunTimes), p)
        }
        scope.launch {
            var last: Inputs? = null
            inputs
                .map { next -> Step(next, fades = last?.switchesEvening(next) == true).also { last = next } }
                .distinctUntilChangedBy { it.inputs.target }
                .collectLatest { step ->
                    if (step.fades) fade(step.inputs.target) else _level.value = step.inputs.target
                }
        }
    }

    private suspend fun fade(to: Float) {
        val from = _level.value
        val steps = (FADE_MS / FADE_STEP_MS).toInt()
        for (i in 1..steps) {
            delay(FADE_STEP_MS)
            _level.value = if (i == steps) to else from + (to - from) * i / steps
        }
    }

    private fun Inputs.switchesEvening(next: Inputs): Boolean =
        evening != next.evening && screen == Screen.AWAKE && next.screen == Screen.AWAKE && inFront && next.inFront
}
