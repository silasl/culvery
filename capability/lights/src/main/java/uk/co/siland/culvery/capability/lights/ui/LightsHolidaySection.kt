package uk.co.siland.culvery.capability.lights.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.lights.Automation
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.HOLIDAY_JUST_LIGHTS
import uk.co.siland.culvery.capability.lights.LightsSettings
import uk.co.siland.culvery.capability.lights.LightsSystem
import uk.co.siland.culvery.capability.lights.NO_AUTOMATIONS
import uk.co.siland.culvery.capability.lights.SET_UP_HUE_FOR_AWAY
import uk.co.siland.culvery.core.plugin.HolidayLightsSection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhType

private const val TAG = "LightsHolidaySection"

/** Holiday lights' presence automations on Holiday mode's page in Settings › Modes (15b decision 5); nothing without a paired bridge. */
@Singleton
class LightsHolidaySection @Inject constructor(
    private val settings: LightsSettings,
    private val bridges: Bridges,
    private val toaster: Toaster,
) : HolidayLightsSection {
    @Composable
    override fun Content(modifier: Modifier) = LightsHolidayHost(settings, bridges, toaster, modifier)
}

/** Reads the bridge's automations as the section opens, and saves each tick at once. */
@Composable
internal fun LightsHolidayHost(settings: LightsSettings, bridges: Bridges, toaster: Toaster, modifier: Modifier = Modifier) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val system by bridges.system.collectAsStateWithLifecycle()
    var automations by remember { mutableStateOf<List<Automation>?>(null) }
    val scope = rememberCoroutineScope()
    val change = scope.saving(toaster) { Log.w(TAG, "Couldn't save a lights setting (${it::class.simpleName})") }
    LaunchedEffect(system) {
        automations = null
        automations = system?.let { readBridge(it) }
    }
    val p = prefs ?: return
    if (system == null) {
        Text(SET_UP_HUE_FOR_AWAY, style = HhType.body, color = Culvery.colors.mute, modifier = modifier.testTag("lights_holiday_set_up"))
        return
    }
    HolidayChoice(automations, p.holidayAutomations, modifier) { ids -> change { settings.setHolidayAutomations(ids) } }
}

/** The automations as ticks; nothing until the bridge has answered. A tick saves only automations the bridge still lists. */
@Composable
internal fun HolidayChoice(
    automations: List<Automation>?,
    holidayAutomations: Set<String>,
    modifier: Modifier = Modifier,
    onHolidayAutomations: (Set<String>) -> Unit,
) {
    val c = Culvery.colors
    val listed = automations ?: return
    if (listed.isEmpty()) {
        Text(NO_AUTOMATIONS, style = HhType.body, color = c.mute, modifier = modifier.testTag("lights_no_automations"))
        return
    }
    val ticked = holidayAutomations intersect listed.map { it.id }.toSet()
    Box(modifier) {
        ChipFlow {
            listed.forEach { automation ->
                HhChoiceChip(
                    automation.name, selected = automation.id in ticked, tag = "lights_automation_${automation.id}", role = Role.Checkbox,
                    onClick = { onHolidayAutomations(if (automation.id in ticked) ticked - automation.id else ticked + automation.id) },
                )
            }
        }
    }
    if (ticked.isEmpty()) Text(HOLIDAY_JUST_LIGHTS, style = HhType.body, color = c.mute, modifier = modifier.testTag("lights_holiday_note"))
}

/** No check runs while only Settings shows, and the choices need the automations. */
private suspend fun readBridge(system: LightsSystem): List<Automation>? = attempt({
    system.readAll()
    system.automations()
}) { e ->
    Log.w(TAG, "Couldn't read the bridge for Holiday mode's page (${e::class.simpleName})")
    null
}
