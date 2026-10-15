package uk.co.siland.culvery.capability.music.ui

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

/** Settings › Music as Settings shows it, beside the 320 dp page list; the address sheet at the right edge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val actions = MusicSettingsActions({}, {}, {}, {})
    private val connected = connectedSettings

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) = compose.snap(name, dark, Modifier.size(960.dp, 800.dp), content)

    @Test fun musicDark() = snap("settings_music_dark", true) { InSettingsPane { MusicSettingsScreen(connected, actions) } }

    @Test fun musicLight() = snap("settings_music_light", false) { InSettingsPane { MusicSettingsScreen(connected, actions) } }

    @Test
    fun unconnectedDark() = snap("settings_music_unconnected_dark", true) {
        InSettingsPane {
            MusicSettingsScreen(
                connected.copy(connected = false, rooms = emptyList(), usualRoomId = null,
                    findLine = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."),
                actions,
            )
        }
    }

    @Test
    fun addressSheetDark() = snap("music_address_sheet_dark", true) {
        Box(Modifier.size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) { AddressSheet(onConnect = { null }, onEdited = {}, onClose = {}) }
    }
}
