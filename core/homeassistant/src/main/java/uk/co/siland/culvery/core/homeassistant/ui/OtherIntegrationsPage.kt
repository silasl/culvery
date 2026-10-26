package uk.co.siland.culvery.core.homeassistant.ui

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.homeassistant.CheckOutcome
import uk.co.siland.culvery.core.homeassistant.EntityChoice
import uk.co.siland.culvery.core.homeassistant.HA_ADDRESS
import uk.co.siland.culvery.core.homeassistant.HA_ADDRESS_EXAMPLE
import uk.co.siland.culvery.core.homeassistant.HA_CHECK
import uk.co.siland.culvery.core.homeassistant.HA_HELP
import uk.co.siland.culvery.core.homeassistant.HA_NONE
import uk.co.siland.culvery.core.homeassistant.HA_TOKEN
import uk.co.siland.culvery.core.homeassistant.HA_TOKEN_PLACEHOLDER
import uk.co.siland.culvery.core.homeassistant.HA_TOKEN_SAVED
import uk.co.siland.culvery.core.homeassistant.HOME_ASSISTANT
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantDevice
import uk.co.siland.culvery.core.homeassistant.HomeAssistantException
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLinking
import uk.co.siland.culvery.core.homeassistant.LINKED
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED_YET
import uk.co.siland.culvery.core.homeassistant.OTHER_INTEGRATIONS
import uk.co.siland.culvery.core.homeassistant.OTHER_INTEGRATIONS_LINE
import uk.co.siland.culvery.core.homeassistant.problemLine
import uk.co.siland.culvery.core.plugin.CHECKING
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.save
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

private const val TAG = "OtherIntegrations"

const val OTHER_INTEGRATIONS_PAGE_ID = "other_integrations"

/** Settings › Other integrations (11 §4.1), admin-only as the rest of Settings; after Lights (470), before Door (500). */
class OtherIntegrationsPage @Inject constructor(
    private val ha: HomeAssistant,
    private val linking: HomeAssistantLinking,
    private val devices: Set<@JvmSuppressWildcards HomeAssistantDevice>,
    private val toaster: Toaster,
) : SettingsPage {
    override val id = OTHER_INTEGRATIONS_PAGE_ID
    override val title = OTHER_INTEGRATIONS
    override val order = 480

    @Composable
    override fun Content() = OtherIntegrationsHost(ha, linking, devices, toaster)
}

/** One kind of device: [choices] is null until Home Assistant has listed them. */
data class DeviceUi(
    val title: String,
    val domain: String,
    val noneFound: String,
    val choices: List<EntityChoice>?,
    val chosen: String?,
    val line: String?,
)

data class OtherIntegrationsUi(
    val linked: Boolean,
    /** The §6 line, which takes the connection line's place. */
    val status: String?,
    val tokenSaved: Boolean,
    val checking: Boolean,
    val checkLine: String?,
    val devices: List<DeviceUi>,
)

@Composable
fun OtherIntegrations(
    ui: OtherIntegrationsUi,
    address: String,
    onAddress: (String) -> Unit,
    token: String,
    onToken: (String) -> Unit,
    onCheck: () -> Unit,
    onChoose: (domain: String, entityId: String?) -> Unit,
) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(HomeAssistantDimens.settingsGap), modifier = Modifier.testTag("settings_other")) {
        HhPageTitle(OTHER_INTEGRATIONS, OTHER_INTEGRATIONS_LINE)
        HhSectionLabel(HOME_ASSISTANT)
        if (ui.status != null) {
            Text(ui.status, style = HhType.body, color = c.danger, modifier = Modifier.testTag("ha_status"))
        } else {
            Text(if (ui.linked) LINKED else NOT_LINKED_YET, style = HhType.body, color = c.mute, modifier = Modifier.testTag("ha_status"))
        }
        HhTextField(address, onAddress, HA_ADDRESS_EXAMPLE, HA_ADDRESS, "ha_address", capitalization = KeyboardCapitalization.None)
        HhTextField(
            token, onToken, if (ui.tokenSaved) HA_TOKEN_SAVED else HA_TOKEN_PLACEHOLDER, HA_TOKEN, "ha_token",
            capitalization = KeyboardCapitalization.None, secret = true,
        )
        Text(HA_HELP, style = HhType.body, color = c.mute, modifier = Modifier.testTag("ha_help"))
        HhButton(
            if (ui.checking) CHECKING else HA_CHECK, onCheck, Modifier.testTag("ha_check"),
            tone = if (ui.linked) ButtonTone.Quiet else ButtonTone.Primary, icon = Icons.CHECK, enabled = !ui.checking, on = ButtonSurface.Bg,
        )
        HhMessageLine(ui.checkLine, HhType.body, c.danger, "ha_check_line")
        if (ui.linked) {
            Column(verticalArrangement = Arrangement.spacedBy(HomeAssistantDimens.deviceGap)) { ui.devices.forEach { DevicePicker(it, onChoose) } }
        }
    }
}

@Composable
private fun DevicePicker(device: DeviceUi, onChoose: (String, String?) -> Unit) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(HomeAssistantDimens.settingsGap), modifier = Modifier.testTag("ha_device_${device.domain}")) {
        HhSectionLabel(device.title)
        val choices = device.choices
        when {
            choices == null -> Unit
            choices.isEmpty() -> Text(device.noneFound, style = HhType.body, color = c.mute, modifier = Modifier.testTag("ha_none_found_${device.domain}"))
            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HomeAssistantDimens.settingsButtonGap),
                verticalArrangement = Arrangement.spacedBy(HomeAssistantDimens.settingsButtonGap),
            ) {
                choices.forEachIndexed { index, choice ->
                    HhChoiceChip(
                        choice.name, selected = choice.id == device.chosen, tag = "ha_choice_${device.domain}_$index",
                        onClick = { onChoose(device.domain, choice.id) },
                    )
                }
                HhChoiceChip(HA_NONE, selected = device.chosen == null, tag = "ha_none_${device.domain}", onClick = { onChoose(device.domain, null) })
            }
        }
        device.line?.let { Text(it, style = HhType.body, color = c.mute, modifier = Modifier.testTag("ha_line_${device.domain}")) }
    }
}

/**
 * Settings › Other integrations with its data. The token lives in this composition only (plain `remember`, so it is
 * never saved with the screen's state) and is cleared once linked; the pickers read Home Assistant's entities as
 * the page opens and and after each Linked Check.
 */
@Composable
internal fun OtherIntegrationsHost(
    ha: HomeAssistant,
    linking: HomeAssistantLinking,
    devices: Set<HomeAssistantDevice>,
    toaster: Toaster,
) {
    val sorted = remember(devices) { devices.sortedBy { it.order } }
    val link by ha.link.collectAsStateWithLifecycle()
    val problem by ha.problem.collectAsStateWithLifecycle()
    var address by remember(link?.baseUrl) { mutableStateOf(link?.baseUrl?.toString().orEmpty()) }
    var token by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var checkLine by remember { mutableStateOf<String?>(null) }
    var linkedCount by remember { mutableIntStateOf(0) }
    var choices by remember { mutableStateOf<Map<String, List<EntityChoice>>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    DisposableEffect(ha, sorted) {
        val stop = ha.watch(watchedBy(sorted))
        onDispose(stop)
    }
    val isLinked = link != null
    // Every Linked Check reads again: it is how the household refreshes the lists after adding a device.
    LaunchedEffect(isLinked, linkedCount) {
        choices = emptyMap()
        if (!isLinked) return@LaunchedEffect
        choices = try {
            linking.entities(sorted.map { it.domain }.toSet())
        } catch (e: HomeAssistantException) {
            emptyMap()
        }
    }
    val deviceUis by remember(sorted, choices) { deviceUis(sorted, choices) }.collectAsStateWithLifecycle(emptyList())
    val change = scope.saving(toaster) { Log.w(TAG, "Couldn't save a Home Assistant choice (${it::class.simpleName})") }

    OtherIntegrations(
        OtherIntegrationsUi(
            linked = link != null, status = problem?.let(::problemLine), tokenSaved = link != null, checking = checking,
            checkLine = checkLine, devices = deviceUis,
        ),
        address = address, onAddress = { address = it },
        token = token, onToken = { token = it },
        onCheck = {
            if (!checking) {
                checking = true
                checkLine = null
                val typed = token
                scope.launch {
                    var outcome: CheckOutcome? = null
                    try {
                        toaster.save({ outcome = linking.check(address, typed) }) {
                            Log.w(TAG, "Couldn't save the Home Assistant link (${it::class.simpleName})")
                        }
                    } finally {
                        checking = false
                    }
                    when (val result = outcome) {
                        CheckOutcome.Linked -> {
                            clearClipboardIfHolds(context, typed)
                            token = ""
                            linkedCount++
                        }
                        is CheckOutcome.Failed -> checkLine = result.line
                        CheckOutcome.PinRefused, null -> Unit
                    }
                }
            }
        },
        onChoose = { domain, entityId -> sorted.firstOrNull { it.domain == domain }?.let { device -> change { device.choose(entityId) } } },
    )
}

private fun watchedBy(devices: List<HomeAssistantDevice>): Flow<Set<String>> =
    if (devices.isEmpty()) flowOf(emptySet()) else combine(devices.map { it.watched }) { sets -> sets.flatMapTo(mutableSetOf()) { it } }

private fun deviceUis(devices: List<HomeAssistantDevice>, choices: Map<String, List<EntityChoice>>): Flow<List<DeviceUi>> =
    if (devices.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(
            devices.map { device ->
                combine(device.chosen, device.line) { chosen, line ->
                    DeviceUi(device.title, device.domain, device.noneFound, choices[device.domain], chosen, line)
                }
            },
        ) { it.toList() }
    }

/** A token pasted from the clipboard shouldn't outlive the field; anything else the household copied stays. */
private fun clearClipboardIfHolds(context: Context, token: String) {
    if (token.isBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val held = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    if (held?.trim() == token.trim()) clipboard.clearPrimaryClip()
}
