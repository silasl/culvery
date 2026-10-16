package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.AWAY
import uk.co.siland.culvery.capability.lights.MIMIC
import uk.co.siland.culvery.capability.lights.room

/** Settings › Lights as Settings shows it, beside the 320 dp page list; the sheets at the right edge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class LightsSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val paired = LightsSettingsUi(
        paired = true, status = null, findLine = null, finding = false,
        rooms = listOf(room("Lounge"), room("Kitchen"), room("Hall"), room("Garden")), mainRoomId = "Kitchen-id",
        leftOut = setOf("Garden-id", "Gone-id"), goneLeftOut = 1, automations = listOf(MIMIC, AWAY), holidayAutomations = setOf("mimic-id"),
    )

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) = compose.snap(name, dark, Modifier.size(960.dp, 800.dp), content)

    private fun page(ui: LightsSettingsUi): @Composable () -> Unit = { InSettingsPane { LightsSettingsPage(ui, {}, {}, {}, {}, {}) } }

    @Test fun lightsDark() = snap("settings_lights_dark", true, page(paired))

    @Test fun lightsLight() = snap("settings_lights_light", false, page(paired))

    @Test
    fun unpairedDark() = snap(
        "settings_lights_unpaired_dark", true,
        page(
            paired.copy(
                paired = false, rooms = emptyList(), automations = null, leftOut = emptySet(), goneLeftOut = 0,
                findLine = "No Hue Bridge answered. Check it’s plugged in, or enter the bridge’s address.",
            ),
        ),
    )

    @Test
    fun pairingSheetDark() = snap("lights_pairing_sheet_dark", true) {
        Box(Modifier.size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) { PairingSheet(PairingState.WAITING, {}, {}) }
    }

    @Test
    fun addressSheetDark() = snap("lights_address_sheet_dark", true) {
        Box(Modifier.size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) { AddressSheet(onConnect = { null }, onEdited = {}, onClose = {}) }
    }
}
