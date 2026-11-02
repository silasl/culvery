package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.ModesStore
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.SettingsTokens

private const val TAG = "HolidayPage"

/**
 * Holiday mode's page (15b §4.2): its head with its own Turn on now, Show on Home and a Needs a PIN switch that is held
 * on, then its own sections, then Plugs. It cannot be renamed, given music or deleted. [page] is null in a build without Holiday.
 */
@Composable
internal fun HolidayPage(
    prefs: ModesPrefs,
    room: Int?,
    controls: ModeControls,
    page: BuiltInModePage?,
    plugs: HousePlugs?,
    store: ModesStore,
    toaster: Toaster,
) {
    val change = rememberCoroutineScope().saving(toaster, logSave)
    val place by remember { controls.place(HouseModes.HOLIDAY) }.collectAsStateWithLifecycle(null)

    ModeHeader(Icons.BEACH_ACCESS, ModesWords.HOLIDAY_NAME, onIcon = null, onName = null) { page?.TurnButton() }
    page?.TurnLine()
    ModeSwitches(
        home = {
            ShowOnHomeSwitch(prefs.holidayOnHome, place, room, prefs) { on -> change { store.update { it.copy(holidayOnHome = on) } } }
        },
        pin = { HhSwitchRow(ModesWords.NEEDS_A_PIN, line = ModesWords.HOLIDAY_PIN, checked = true, tag = "mode_pin", enabled = false, onChange = {}) },
    )
    page?.Sections()
    if (plugs != null) {
        val added by remember(plugs) { plugs.addedOrNone(HouseModes.HOLIDAY, TAG) }.collectAsStateWithLifecycle(emptyList())
        ModePlugsSection(HouseModes.HOLIDAY, plugs, added, onLockedAdded = {}, toaster = toaster)
    }
}

/** A mode page's Show on Home and Needs a PIN, side by side and the same width. */
@Composable
internal fun ModeSwitches(home: @Composable () -> Unit, pin: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f)) { home() }
        Row(Modifier.weight(1f)) { pin() }
    }
}

/** Show on Home: on while the mode is on Home with a place there; off with no room left, it says so and can't be turned on. */
@Composable
internal fun ShowOnHomeSwitch(showOnHome: Boolean, place: Int?, room: Int?, prefs: ModesPrefs, onChange: (Boolean) -> Unit) {
    val shown = showOnHome && place != null
    val canShow = room != null && !showOnHome && prefs.onHome() < room
    HhSwitchRow(
        ModesWords.SHOW_ON_HOME, line = room?.takeIf { !shown && !canShow }?.let(ModesWords::homeHasRoom),
        checked = shown, tag = "mode_home", enabled = shown || canShow, onChange = onChange,
    )
}
