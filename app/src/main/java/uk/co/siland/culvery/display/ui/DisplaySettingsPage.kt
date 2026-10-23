package uk.co.siland.culvery.display.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import javax.inject.Inject
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.FROM
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhSliderRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhTimeStepperRow
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.UNTIL
import uk.co.siland.culvery.display.ALLOW_THE_CAMERA
import uk.co.siland.culvery.display.BRIGHTNESS
import uk.co.siland.culvery.display.BRIGHTNESS_RANGE
import uk.co.siland.culvery.display.CAMERA_NOT_ASKED
import uk.co.siland.culvery.display.CAMERA_REFUSED
import uk.co.siland.culvery.display.CANT_OPEN_ANDROIDS_SETTINGS
import uk.co.siland.culvery.display.CameraAccess
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DAYTIME
import uk.co.siland.culvery.display.DISPLAY
import uk.co.siland.culvery.display.DisplayPrefs
import uk.co.siland.culvery.display.DisplaySettings
import uk.co.siland.culvery.display.EVENING
import uk.co.siland.culvery.display.NIGHT_CLOCK
import uk.co.siland.culvery.display.NIGHT_END
import uk.co.siland.culvery.display.NIGHT_START
import uk.co.siland.culvery.display.NO_FRONT_CAMERA
import uk.co.siland.culvery.display.NO_SUN_TIMES
import uk.co.siland.culvery.display.OPEN_ANDROIDS_SETTINGS
import uk.co.siland.culvery.display.SLEEP
import uk.co.siland.culvery.display.SLEEP_CHOICES
import uk.co.siland.culvery.display.SLEEP_WHEN_ALONE
import uk.co.siland.culvery.display.WAKE_BY_CAMERA
import uk.co.siland.culvery.display.WAKE_BY_CAMERA_DEFAULT
import uk.co.siland.culvery.display.WAKE_BY_CAMERA_LINE
import uk.co.siland.culvery.display.appDetailsIntents
import uk.co.siland.culvery.display.minutes
import uk.co.siland.culvery.display.nightClockLine
import uk.co.siland.culvery.display.sleepAfter
import uk.co.siland.culvery.shell.SunToday

private const val TAG = "DisplaySettings"

const val DISPLAY_PAGE_ID = "display"

/** Settings › Display (8 §4.6), Admin only like the rest of Settings; after Calendars (400), before Music. */
class DisplayPage @Inject constructor(
    private val settings: DisplaySettings,
    private val access: CameraAccess,
    private val sunToday: SunToday,
    private val toaster: Toaster,
) : SettingsPage {
    override val id = DISPLAY_PAGE_ID
    override val title = DISPLAY
    override val order = 440

    @Composable
    override fun Content() {
        DisplaySettingsHost(settings, access, sunToday, toaster, LocalShellNavigator.current)
    }
}

data class DisplaySettingsUi(val prefs: DisplayPrefs, val camera: CameraState, val sunKnown: Boolean)

class DisplaySettingsActions(
    val onNight: (LocalTime, LocalTime) -> Unit,
    val onDay: (Int) -> Unit,
    val onEvening: (Int) -> Unit,
    val onSleepAfter: (Int) -> Unit,
    val onWakeByCamera: (Boolean) -> Unit,
    val onAllowCamera: () -> Unit,
    val onOpenAndroidSettings: () -> Unit,
)

@Composable
fun DisplaySettingsScreen(ui: DisplaySettingsUi, actions: DisplaySettingsActions) {
    val c = Culvery.colors
    val prefs = ui.prefs
    Column(verticalArrangement = Arrangement.spacedBy(DisplayDimens.settingsGap), modifier = Modifier.testTag("settings_display")) {
        HhPageTitle(DISPLAY)
        HhSectionLabel(NIGHT_CLOCK)
        Text(nightClockLine(prefs.nightFrom, prefs.nightUntil), style = HhType.body, color = c.mute, modifier = Modifier.testTag("display_night_line"))
        HhTimeStepperRow(FROM, NIGHT_START, prefs.nightFrom, "display_from") { actions.onNight(it, prefs.nightUntil) }
        HhTimeStepperRow(UNTIL, NIGHT_END, prefs.nightUntil, "display_until") { actions.onNight(prefs.nightFrom, it) }
        HhSectionLabel(BRIGHTNESS)
        BrightnessRow(DAYTIME, prefs.dayPercent, "display_day", actions.onDay)
        BrightnessRow(EVENING, prefs.eveningPercent, "display_evening", actions.onEvening)
        if (!ui.sunKnown) {
            Text(NO_SUN_TIMES, style = HhType.body, color = c.mute, modifier = Modifier.testTag("display_no_sun"))
        }
        HhSectionLabel(SLEEP)
        Text(SLEEP_WHEN_ALONE, style = HhType.rowTitle, color = c.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap), verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
            SLEEP_CHOICES.forEach { m ->
                HhChoiceChip(minutes(m), prefs.sleepAfterMinutes == m, "display_sleep_$m", { actions.onSleepAfter(m) }, spokenName = sleepAfter(m))
            }
        }
        CameraRow(ui, actions)
    }
}

/** Saved when the slider is let go, not at each step of a drag. */
@Composable
private fun BrightnessRow(name: String, percent: Int, tag: String, onLetGo: (Int) -> Unit) =
    HhSliderRow(name, percent, tag, onLetGo, range = BRIGHTNESS_RANGE)

@Composable
private fun CameraRow(ui: DisplaySettingsUi, actions: DisplaySettingsActions) {
    when (ui.camera) {
        CameraState.ALLOWED -> HhSwitchRow(WAKE_BY_CAMERA, WAKE_BY_CAMERA_LINE, ui.prefs.wakeByCamera ?: WAKE_BY_CAMERA_DEFAULT, "display_camera", actions.onWakeByCamera)
        CameraState.NOT_ASKED -> CameraNote(CAMERA_NOT_ASKED) {
            HhButton(ALLOW_THE_CAMERA, actions.onAllowCamera, Modifier.testTag("display_allow_camera"), on = ButtonSurface.Bg)
        }
        CameraState.REFUSED -> CameraNote(CAMERA_REFUSED) {
            HhButton(OPEN_ANDROIDS_SETTINGS, actions.onOpenAndroidSettings, Modifier.testTag("display_open_settings"), on = ButtonSurface.Bg)
        }
        CameraState.NO_CAMERA -> CameraNote(NO_FRONT_CAMERA)
    }
}

/** Why the camera switch isn't offered, with what to do about it if anything. */
@Composable
private fun CameraNote(text: String, button: (@Composable () -> Unit)? = null) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
        Text(WAKE_BY_CAMERA, style = HhType.rowTitle, color = c.ink)
        Text(text, style = HhType.body, color = c.mute)
        button?.invoke()
    }
}

/** Settings › Display with its data; says when a setting didn't save, and waits for the saved settings before it draws. */
@Composable
internal fun DisplaySettingsHost(
    settings: DisplaySettings,
    access: CameraAccess,
    sunToday: SunToday,
    toaster: Toaster,
    navigator: ShellNavigator,
) {
    val prefs by settings.read.collectAsStateWithLifecycle()
    val camera by access.state.collectAsStateWithLifecycle()
    val sun by sunToday.times.collectAsStateWithLifecycle()
    val packageName = LocalContext.current.packageName
    val scope = rememberCoroutineScope()
    val p = prefs ?: return
    val change = scope.saving(toaster) { Log.w(TAG, "Couldn't save a display setting (${it::class.simpleName})") }
    DisplaySettingsScreen(
        DisplaySettingsUi(p, camera, sunKnown = sun != null),
        DisplaySettingsActions(
            onNight = { from, until -> change { settings.setNight(from, until) } },
            onDay = { v -> change { settings.setDayPercent(v) } },
            onEvening = { v -> change { settings.setEveningPercent(v) } },
            onSleepAfter = { m -> change { settings.setSleepAfter(m) } },
            onWakeByCamera = { on -> change { settings.setWakeByCamera(on) } },
            onAllowCamera = access::ask,
            onOpenAndroidSettings = { navigator.openSystemScreen(appDetailsIntents(packageName), CANT_OPEN_ANDROIDS_SETTINGS) },
        ),
    )
}
