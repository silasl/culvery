package uk.co.siland.culvery.display.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import javax.inject.Inject
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
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
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhDateGrid
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSliderRow
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhTimeStepperRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.UNTIL
import uk.co.siland.culvery.core.ui.defaultSeasonDates
import uk.co.siland.culvery.display.ALLOW_THE_CAMERA
import uk.co.siland.culvery.display.BRIGHTNESS
import uk.co.siland.culvery.display.BRIGHTNESS_RANGE
import uk.co.siland.culvery.display.CAMERA_NOT_ASKED
import uk.co.siland.culvery.display.CAMERA_REFUSED
import uk.co.siland.culvery.display.CANCEL
import uk.co.siland.culvery.display.CANT_OPEN_ANDROIDS_SETTINGS
import uk.co.siland.culvery.display.CHANGE_THE_DATES
import uk.co.siland.culvery.display.CameraAccess
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DAYTIME
import uk.co.siland.culvery.display.DAYTIME_BRIGHTNESS
import uk.co.siland.culvery.display.DISPLAY
import uk.co.siland.culvery.display.DisplayPrefs
import uk.co.siland.culvery.display.DisplaySettings
import uk.co.siland.culvery.display.EVENING
import uk.co.siland.culvery.display.EVENING_BRIGHTNESS
import uk.co.siland.culvery.display.NIGHT_CLOCK
import uk.co.siland.culvery.display.NIGHT_END
import uk.co.siland.culvery.display.NIGHT_START
import uk.co.siland.culvery.display.NO_FRONT_CAMERA
import uk.co.siland.culvery.display.NO_SUN_TIMES
import uk.co.siland.culvery.display.OPEN_ANDROIDS_SETTINGS
import uk.co.siland.culvery.display.SAVE_DATES
import uk.co.siland.culvery.display.SEASONAL_THEMES
import uk.co.siland.culvery.display.SLEEP
import uk.co.siland.culvery.display.SLEEP_CHOICES
import uk.co.siland.culvery.display.SLEEP_WHEN_ALONE
import uk.co.siland.culvery.display.SeasonalThemes
import uk.co.siland.culvery.display.TAP_FIRST_THEN_LAST
import uk.co.siland.culvery.display.WAKE_BY_CAMERA
import uk.co.siland.culvery.display.WAKE_BY_CAMERA_DEFAULT
import uk.co.siland.culvery.display.WAKE_BY_CAMERA_LINE
import uk.co.siland.culvery.display.appDetailsIntents
import uk.co.siland.culvery.display.datesLine
import uk.co.siland.culvery.display.datesTitle
import uk.co.siland.culvery.display.lastDayBeforeFirst
import uk.co.siland.culvery.display.longerThanAYear
import uk.co.siland.culvery.display.minutes
import uk.co.siland.culvery.display.nextLine
import uk.co.siland.culvery.display.nightClockLine
import uk.co.siland.culvery.display.previewLine
import uk.co.siland.culvery.display.previewTitle
import uk.co.siland.culvery.display.putBack
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
    private val themes: SeasonalThemes,
) : SettingsPage {
    override val id = DISPLAY_PAGE_ID
    override val title = DISPLAY
    override val order = 440

    @Composable
    override fun Content() {
        DisplaySettingsHost(settings, access, sunToday, toaster, LocalShellNavigator.current, themes)
    }
}

data class DisplaySettingsUi(
    val prefs: DisplayPrefs,
    val camera: CameraState,
    val sunKnown: Boolean,
    val today: LocalDate,
    val previewing: SeasonalTheme? = null,
    val dates: Map<SeasonalTheme, SeasonDates> = defaultSeasonDates(),
    val next: Pair<SeasonalTheme, LocalDate>? = null,
)

class DisplaySettingsActions(
    val onNight: (LocalTime, LocalTime) -> Unit,
    val onDay: (Int) -> Unit,
    val onEvening: (Int) -> Unit,
    val onSleepAfter: (Int) -> Unit,
    val onWakeByCamera: (Boolean) -> Unit,
    val onAllowCamera: () -> Unit,
    val onOpenAndroidSettings: () -> Unit,
    val onPreview: (SeasonalTheme, Boolean) -> Unit = { _, _ -> },
    /** Null puts back the theme's default dates. */
    val onDates: (SeasonalTheme, SeasonDates?) -> Unit = { _, _ -> },
)

/** Why [first] to [last] can't be saved, or null (14 §4.5). */
internal fun checkRange(first: LocalDate, last: LocalDate): String? = when {
    last.isBefore(first) -> lastDayBeforeFirst(first)
    !last.isBefore(first.plusYears(1)) -> longerThanAYear(first.plusYears(1))
    else -> null
}

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
        HhSliderRow(DAYTIME, prefs.dayPercent, "display_day", actions.onDay, range = BRIGHTNESS_RANGE, spokenName = DAYTIME_BRIGHTNESS)
        HhSliderRow(EVENING, prefs.eveningPercent, "display_evening", actions.onEvening, range = BRIGHTNESS_RANGE, spokenName = EVENING_BRIGHTNESS)
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
        HhSectionLabel(SEASONAL_THEMES)
        val overlay = LocalOverlayHost.current
        SeasonalTheme.entries.forEach { theme ->
            val on = ui.previewing == theme
            val dates = ui.dates[theme] ?: theme.defaultDates
            HhListRow(
                theme.label,
                subtitle = datesLine(dates),
                onClick = {
                    overlay.show {
                        DatesSheet(
                            theme,
                            dates,
                            ui.today,
                            onSave = { actions.onDates(theme, it) },
                            onReset = { actions.onDates(theme, null) },
                            onEdited = overlay::markEdited,
                            onClose = overlay::dismiss,
                        )
                    }
                },
                clickLabel = CHANGE_THE_DATES,
                modifier = Modifier.testTag("display_dates_${theme.name.lowercase()}"),
            )
            HhSwitchRow(previewTitle(theme.label), previewLine(theme.label, on), on, "display_preview_${theme.name.lowercase()}", { actions.onPreview(theme, it) })
        }
        ui.next?.let { (theme, date) ->
            Text(nextLine(theme, date), style = HhType.body, color = c.mute, modifier = Modifier.testTag("display_next"))
        }
    }
}

/** A theme's dates sheet: the first tap is the first day, the second the last; Save waits for a last day that passes [checkRange] (14 §4.5). */
@Composable
private fun DatesSheet(
    theme: SeasonalTheme,
    saved: SeasonDates,
    today: LocalDate,
    onSave: (SeasonDates) -> Unit,
    onReset: () -> Unit,
    onEdited: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = Culvery.colors
    var month by remember {
        val start = saved.start.atYear(today.year)
        mutableStateOf(YearMonth.from(if (start.isBefore(today)) start.plusYears(1) else start))
    }
    var first by remember { mutableStateOf<LocalDate?>(null) }
    var last by remember { mutableStateOf<LocalDate?>(null) }
    val firstDay = first
    val lastDay = last
    val refusal = if (firstDay != null && lastDay != null) checkRange(firstDay, lastDay) else null
    val range = if (firstDay != null && lastDay != null && refusal == null) firstDay to lastDay else null
    fun pick(day: LocalDate) {
        if (firstDay == null || range != null) {
            first = day
            last = null
        } else {
            last = day
        }
        onEdited(true)
    }
    HhSheet(
        padding = PaddingValues(horizontal = DisplayDimens.sheetPaddingH, vertical = DisplayDimens.sheetPaddingV),
        modifier = Modifier.testTag("display_dates_sheet").semantics { paneTitle = datesTitle(theme.label) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(datesTitle(theme.label), style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(TAP_FIRST_THEN_LAST, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HhDateGrid(
                month = month,
                onMonth = { month = it },
                today = today,
                chosen = setOfNotNull(firstDay, range?.second),
                band = range?.let { (from, to) -> from..to },
                allowed = { true },
                dots = emptyMap(),
                spoken = { "" },
                onPick = ::pick,
            )
            HhMessageLine(refusal, HhType.body, c.danger, "display_dates_refused", gapAbove = DisplayDimens.settingsGap)
        }
        HhFooterButton(
            SAVE_DATES,
            {
                range?.let { (from, to) -> onSave(SeasonDates(MonthDay.from(from), MonthDay.from(to))) }
                onClose()
            },
            "display_dates_save",
            enabled = range != null,
        )
        HhButton(putBack(theme.defaultDates), { onReset(); onClose() }, Modifier.testTag("display_dates_reset"), on = ButtonSurface.Bg)
        HhButton(CANCEL, onClose, Modifier.testTag("display_dates_cancel"), on = ButtonSurface.Bg)
    }
}

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
    themes: SeasonalThemes,
) {
    val prefs by settings.read.collectAsStateWithLifecycle()
    val camera by access.state.collectAsStateWithLifecycle()
    val sun by sunToday.times.collectAsStateWithLifecycle()
    val previewing by themes.previewing.collectAsStateWithLifecycle()
    val dates by themes.dates.collectAsStateWithLifecycle()
    val next by themes.next.collectAsStateWithLifecycle()
    val today by themes.today.collectAsStateWithLifecycle()
    val packageName = LocalContext.current.packageName
    val scope = rememberCoroutineScope()
    val p = prefs ?: return
    val change = scope.saving(toaster) { Log.w(TAG, "Couldn't save a display setting (${it::class.simpleName})") }
    DisplaySettingsScreen(
        // The device date only places the grid until the household clock is read; it never picks the theme.
        DisplaySettingsUi(p, camera, sunKnown = sun != null, today = today ?: LocalDate.now(), previewing = previewing, dates = dates, next = next),
        DisplaySettingsActions(
            onNight = { from, until -> change { settings.setNight(from, until) } },
            onDay = { v -> change { settings.setDayPercent(v) } },
            onEvening = { v -> change { settings.setEveningPercent(v) } },
            onSleepAfter = { m -> change { settings.setSleepAfter(m) } },
            onWakeByCamera = { on -> change { settings.setWakeByCamera(on) } },
            onAllowCamera = access::ask,
            onOpenAndroidSettings = { navigator.openSystemScreen(appDetailsIntents(packageName), CANT_OPEN_ANDROIDS_SETTINGS) },
            onPreview = themes::preview,
            onDates = { theme, chosen ->
                change { if (chosen == null) settings.resetSeasonDates(theme) else settings.setSeasonDates(theme, chosen) }
            },
        ),
    )
}
