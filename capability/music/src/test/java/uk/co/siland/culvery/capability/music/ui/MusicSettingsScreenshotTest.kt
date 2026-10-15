package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Settings › Music as Settings shows it, beside the 320 dp page list; the address sheet at the right edge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val actions = MusicSettingsActions({}, {}, {}, {})
    private val connected = MusicSettingsUi(
        connected = true, status = null, findLine = null, finding = false,
        rooms = listOf(SpeakerRoom("RINCON_A", "Kitchen", 30), SpeakerRoom("RINCON_B", "Lounge", 40), SpeakerRoom("RINCON_C", "Dining room", 35)),
        usualRoomId = "RINCON_A", lowerTo = 20,
    )

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { CulveryTheme(dark = dark) { Box(Modifier.size(960.dp, 800.dp).background(Culvery.colors.bg)) { content() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

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
