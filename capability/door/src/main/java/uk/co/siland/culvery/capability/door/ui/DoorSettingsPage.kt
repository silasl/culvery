package uk.co.siland.culvery.capability.door.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.door.ACCESS_STEP
import uk.co.siland.culvery.capability.door.ACCESS_STEP_LINE
import uk.co.siland.culvery.capability.door.CAPTURE_LINE
import uk.co.siland.culvery.capability.door.CAPTURE_MS
import uk.co.siland.culvery.capability.door.CHIME
import uk.co.siland.culvery.capability.door.CHIME_VOLUME
import uk.co.siland.culvery.capability.door.Chime
import uk.co.siland.culvery.capability.door.DONE
import uk.co.siland.culvery.capability.door.DOOR
import uk.co.siland.culvery.capability.door.DoorHealth
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.capability.door.DoorSettings
import uk.co.siland.culvery.capability.door.EARLIER
import uk.co.siland.culvery.capability.door.FROM
import uk.co.siland.culvery.capability.door.LATER
import uk.co.siland.culvery.capability.door.NOTIFICATION_ACCESS_MISSING
import uk.co.siland.culvery.capability.door.NOT_DONE_YET
import uk.co.siland.culvery.capability.door.OPEN_NOTIFICATION_ACCESS
import uk.co.siland.culvery.capability.door.PLAY_CHIME
import uk.co.siland.culvery.capability.door.QUIET_HOURS
import uk.co.siland.culvery.capability.door.UNTIL
import uk.co.siland.culvery.capability.door.appStep
import uk.co.siland.culvery.capability.door.checkNotifications
import uk.co.siland.culvery.capability.door.clockText
import uk.co.siland.culvery.capability.door.lastHeard
import uk.co.siland.culvery.capability.door.quietHoursLine
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens

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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings_door"),
    ) {
        Text(DOOR, style = HhType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        HhSectionLabel(CHIME)
        VolumeRow(ui.volume, actions.onVolume)
        HhButton(PLAY_CHIME, actions.onPlayChime, Modifier.testTag("door_play_chime"), icon = Icons.DOORBELL, on = ButtonSurface.Bg)
        HhSectionLabel(QUIET_HOURS)
        SwitchRow(QUIET_HOURS, quietHoursLine(ui.quietFrom, ui.quietUntil), ui.quietOn, "door_quiet") { actions.onQuietHours(it, ui.quietFrom, ui.quietUntil) }
        StepperRow(FROM, "quiet hours start", ui.quietFrom, "door_from") { actions.onQuietHours(ui.quietOn, it, ui.quietUntil) }
        StepperRow(UNTIL, "quiet hours end", ui.quietUntil, "door_until") { actions.onQuietHours(ui.quietOn, ui.quietFrom, it) }
        HhSectionLabel(ui.service)
        ui.status?.let { DoorStatusLine(it) }
        Text(ui.lastHeard, style = HhType.body, color = c.mute, modifier = Modifier.testTag("door_last_heard"))
        StepRow(appStep(ui.service), ui.hint, done = ui.appInstalled, tag = "door_step_app")
        StepRow(ACCESS_STEP, ACCESS_STEP_LINE, done = ui.accessAllowed, tag = "door_step_access", onClick = actions.onAllowAccess)
        SwitchRow(checkNotifications(ui.service), CAPTURE_LINE, ui.capturing, "door_capture", actions.onCapture)
    }
}

/** Saved when the slider is let go, not at each step of a drag. */
@Composable
private fun VolumeRow(volume: Float, onVolume: (Float) -> Unit) {
    val c = Culvery.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: volume
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap)) {
        Text(CHIME_VOLUME, style = HhType.rowTitle, color = c.ink)
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onVolume)
                dragging = null
            },
            steps = DoorDimens.VOLUME_STEPS,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surf3),
            modifier = Modifier.weight(1f).testTag("door_volume").semantics { contentDescription = CHIME_VOLUME },
        )
        Text("${(shown * 100).roundToInt()} %", style = HhType.body, color = c.mute)
    }
}

/** One switch for TalkBack and the finger; [HhSwitch] only draws. */
@Composable
private fun SwitchRow(title: String, line: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ListRowTokens.radius))
            .background(c.surf)
            .testTag(tag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HhType.rowTitle, color = c.ink)
            if (line != null) Text(line, style = HhType.secondary, color = c.mute)
        }
        HhSwitch(checked, onCheckedChange = null, tag = "${tag}_switch")
    }
}

@Composable
private fun StepperRow(label: String, subject: String, time: LocalTime, tag: String, onTime: (LocalTime) -> Unit) {
    HhListRow(
        title = label,
        subtitle = clockText(time),
        modifier = Modifier.testTag(tag),
        trailing = {
            HhButton(EARLIER, { onTime(time.minusMinutes(DoorDimens.QUIET_STEP_MINUTES)) }, Modifier.testTag("${tag}_earlier"), on = ButtonSurface.Surf, spokenName = "$EARLIER, $subject")
            HhButton(LATER, { onTime(time.plusMinutes(DoorDimens.QUIET_STEP_MINUTES)) }, Modifier.testTag("${tag}_later"), on = ButtonSurface.Surf, spokenName = "$LATER, $subject")
        },
    )
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
    fun change(write: suspend () -> Unit) {
        scope.launch { save(toaster, write) }
    }
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
            onPlayChime = { chime.play(p.volume) },
            onQuietHours = { on, from, until -> change { settings.setQuietHours(on, from, until) } },
            onAllowAccess = { navigator.openSystemScreen(provider.accessScreens(), NOTIFICATION_ACCESS_MISSING) },
            onCapture = { on -> change { settings.setCapture(if (on) clock.nowMillis() + CAPTURE_MS else 0L) } },
        ),
    )
}

/** A setting that didn't save says so; nothing changed. */
private suspend fun save(toaster: Toaster, write: suspend () -> Unit) {
    try {
        write()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't save a door setting (${e::class.simpleName})")
        toaster.show(COULD_NOT_SAVE, urgent = true)
    }
}
