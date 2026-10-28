package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.ui.test.snap

/** Settings › Other integrations › Plugs (12 §4.2). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val two = PlugsSettingsUi(
        rows = listOf(
            PlugRowUi("switch.fan", "Fan", shown = false, needsPin = false, line = null),
            PlugRowUi("switch.lamp", "Lamp", shown = true, needsPin = true, line = null),
        ),
        line = null,
    )

    private fun page(name: String, dark: Boolean, ui: PlugsSettingsUi) =
        compose.snap(name, dark, Modifier.width(900.dp)) {
            Column(Modifier.fillMaxWidth().padding(48.dp)) { PlugsSettings(ui, onShow = { _, _ -> }, onNeedsPin = { _, _ -> }) }
        }

    @Test fun listDark() = page("plugs_settings_list_dark", true, two)

    @Test fun listLight() = page("plugs_settings_list_light", false, two)

    @Test fun noneDark() = page("plugs_settings_none_dark", true, PlugsSettingsUi(emptyList(), PlugsWords.NO_TP_LINK_PLUGS))

    @Test fun noneLight() = page("plugs_settings_none_light", false, PlugsSettingsUi(emptyList(), PlugsWords.NO_TP_LINK_PLUGS))

    @Test fun unreadableDark() = page("plugs_settings_unreadable_dark", true, PlugsSettingsUi(emptyList(), PlugsWords.CANT_LIST_PLUGS))

    @Test fun unreadableLight() = page("plugs_settings_unreadable_light", false, PlugsSettingsUi(emptyList(), PlugsWords.CANT_LIST_PLUGS))
}
