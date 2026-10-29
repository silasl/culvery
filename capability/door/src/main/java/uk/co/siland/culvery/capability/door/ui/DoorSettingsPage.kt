package uk.co.siland.culvery.capability.door.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.door.ACCESS_STEP
import uk.co.siland.culvery.capability.door.ACCESS_STEP_LINE
import uk.co.siland.culvery.capability.door.CAPTURE_LINE
import uk.co.siland.culvery.capability.door.CAPTURE_MS
import uk.co.siland.culvery.capability.door.CHIME
import uk.co.siland.culvery.capability.door.CHIME_VOLUME
import uk.co.siland.culvery.capability.door.Chime
import uk.co.siland.culvery.capability.door.DoorSound
import uk.co.siland.culvery.capability.door.DONE
import uk.co.siland.culvery.capability.door.DOOR
import uk.co.siland.culvery.capability.door.DoorHealth
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.capability.door.DoorSettings
import uk.co.siland.culvery.capability.door.NOTIFICATION_ACCESS_MISSING
import uk.co.siland.culvery.capability.door.NOT_DONE_YET
import uk.co.siland.culvery.capability.door.OPEN_NOTIFICATION_ACCESS
import uk.co.siland.culvery.capability.door.PLAY_CHIME
import uk.co.siland.culvery.capability.door.QUIET_HOURS
import uk.co.siland.culvery.capability.door.appStep
import uk.co.siland.culvery.capability.door.checkNotifications
import uk.co.siland.culvery.capability.door.lastHeard
import uk.co.siland.culvery.capability.door.quietHoursLine
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.FROM
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSliderRow
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhTimeStepperRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.UNTIL

private const val TAG = "DoorSettings"

const val DOOR_PAGE_ID = "door"

/** Settings › Door (5 §5.4), admin-only as the rest of Settings; after Calendars (400), before Tablet lock (900). */
class DoorPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = DOOR_PAGE_ID
    override val title = DOOR
    override val order = 500

    @Composable
    override fun Content() = content()
}

data class DoorSettingsUi(
    val service: String,
    val hint: String?,
    val volume: Float,
    val quietOn: Boolean,
    val quietFrom: LocalTime,
    val quietUntil: LocalTime,
    val status: String?,
    val lastHeard: String,
    val appInstalled: Boolean,
    val accessAllowed: Boolean,
    val capturing: Boolean,
)

class DoorSettingsActions(
    val onVolume: (Float) -> Unit,
    val onPlayChime: () -> Unit,
    val onQuietHours: (on: Boolean, from: LocalTime, until: LocalTime) -> Unit,
    val onAllowAccess: () -> Unit,
    val onCapture: (Boolean) -> Unit,
)

@Composable
fun DoorSettingsScreen(ui: DoorSettingsUi, actions: DoorSettingsActions) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(DoorDimens.settingsGap),
        modifier = Modifier.testTag("settings_door"),
    ) {
        HhPageTitle(DOOR)
        HhSectionLabel(CHIME)
        HhSliderRow(
            CHIME_VOLUME, (ui.volume * 100).roundToInt(), "door_volume",
            onLetGo = { actions.onVolume(it / 100f) }, steps = DoorDimens.VOLUME_STEPS,
        )
        HhButton(PLAY_CHIME, actions.onPlayChime, Modifier.testTag("door_play_chime"), icon = Icons.DOORBELL, on = ButtonSurface.Bg)
        HhSectionLabel(QUIET_HOURS)
        HhSwitchRow(QUIET_HOURS, quietHoursLine(ui.quietFrom, ui.quietUntil), ui.quietOn, "door_quiet", onChange = { actions.onQuietHours(it, ui.quietFrom, ui.quietUntil) })
        HhTimeStepperRow(FROM, "quiet hours start", ui.quietFrom, "door_from") { actions.onQuietHours(ui.quietOn, it, ui.quietUntil) }
        HhTimeStepperRow(UNTIL, "quiet hours end", ui.quietUntil, "door_until") { actions.onQuietHours(ui.quietOn, ui.quietFrom, it) }
        HhSectionLabel(ui.service)
        ui.status?.let { DoorStatusLine(it) }
        Text(ui.lastHeard, style = HhType.body, color = c.mute, modifier = Modifier.testTag("door_last_heard"))
        StepRow(appStep(ui.service), ui.hint, done = ui.appInstalled, tag = "door_step_app")
        StepRow(ACCESS_STEP, ACCESS_STEP_LINE, done = ui.accessAllowed, tag = "door_step_access", onClick = actions.onAllowAccess)
        HhSwitchRow(checkNotifications(ui.service), CAPTURE_LINE, ui.capturing, "door_capture", actions.onCapture)
    }
}

/** A connect step (5 §5.5), ticked when done. */
@Composable
private fun StepRow(title: String, line: String?, done: Boolean, tag: String, onClick: (() -> Unit)? = null) {
    val c = Culvery.colors
    HhListRow(
        title = title,
        subtitle = line,
        modifier = Modifier.testTag(tag).semantics { stateDescription = if (done) DONE else NOT_DONE_YET },
        leading = {
            HhIcon(
                if (done) Icons.CHECK else Icons.INFO,
                size = ListRowTokens.icon,
                tint = if (done) c.accent else c.mute,
                modifier = if (done) Modifier.testTag("${tag}_done") else Modifier,
            )
        },
        onClick = onClick,
        clickLabel = onClick?.let { OPEN_NOTIFICATION_ACCESS },
    )
}

/** Settings › Door with its data; says when a setting didn't save. */
@Composable
internal fun DoorSettingsHost(
    settings: DoorSettings,
    health: DoorHealth,
    provider: DoorProvider,
    chime: Chime,
    clock: HouseholdClock,
    zone: Flow<ZoneId>,
    toaster: Toaster,
) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val checks by health.checks.collectAsStateWithLifecycle()
    val status by health.status.collectAsStateWithLifecycle(null)
    val householdZone by zone.collectAsStateWithLifecycle(null)
    val now = rememberNowMillis(clock)
    val navigator = LocalShellNavigator.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { health.refresh() }
    val p = prefs ?: return
    val z = householdZone ?: return
    val change = scope.saving(toaster) { Log.w(TAG, "Couldn't save a door setting (${it::class.simpleName})") }
    val service = provider.descriptor.displayName
    DoorSettingsScreen(
        DoorSettingsUi(
            service = service,
            hint = provider.descriptor.connectHint,
            volume = p.volume,
            quietOn = p.quietOn,
            quietFrom = p.quietFrom,
            quietUntil = p.quietUntil,
            status = status,
            lastHeard = lastHeard(service, p.lastHeardMillis, z),
            appInstalled = checks?.appInstalled == true,
            accessAllowed = checks?.accessAllowed == true,
            capturing = p.capturingAt(now),
        ),
        DoorSettingsActions(
            onVolume = { v -> change { settings.setVolume(v) } },
            onPlayChime = { chime.play(DoorSound.CLASSIC, p.volume) },
            onQuietHours = { on, from, until -> change { settings.setQuietHours(on, from, until) } },
            onAllowAccess = { navigator.openSystemScreen(provider.accessScreens(), NOTIFICATION_ACCESS_MISSING) },
            onCapture = { on -> change { settings.setCapture(if (on) clock.nowMillis() + CAPTURE_MS else 0L) } },
        ),
    )
}
