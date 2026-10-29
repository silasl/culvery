package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import uk.co.siland.culvery.capability.party.KITCHEN
import uk.co.siland.culvery.capability.party.LOUNGE
import uk.co.siland.culvery.capability.party.PARTY_MIX
import uk.co.siland.culvery.capability.party.PartyPrefs
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.test.snap

/** Settings › Party mode as Settings shows it, in the 960 dp pane beside the page list; the music sheet at the right edge (13 §3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class PartySettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val chosen = PartySettingsUi(
        PartyPrefs(music = PARTY_MIX, rooms = listOf(KITCHEN, LOUNGE), scenes = listOf(BRIGHT_IN_KITCHEN)),
        MUSIC_OPTIONS, LIGHT_OPTIONS,
    )

    private fun pane(name: String, dark: Boolean, ui: PartySettingsUi) = compose.snap(name, dark, Modifier.size(960.dp, 800.dp)) {
        CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
            InSettingsPane { PartySettingsScreen(ui, onMusic = {}, onSpeaker = { _, _ -> }, onScene = { _, _ -> }) }
        }
    }

    private fun sheet(name: String, dark: Boolean, content: @Composable () -> Unit) = compose.snap(name, dark, Modifier.size(960.dp, 800.dp)) {
        Box(Modifier.size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) { content() }
    }

    @Test fun settingsDark() = pane("party_settings_dark", true, chosen)

    @Test fun settingsLight() = pane("party_settings_light", false, chosen)

    @Test fun unlinkedDark() = pane("party_settings_unlinked_dark", true, chosen.copy(music = null, lights = null))

    @Test fun unlinkedLight() = pane("party_settings_unlinked_light", false, chosen.copy(music = null, lights = null))

    @Test
    fun musicSheetDark() = sheet("party_settings_music_sheet_dark", true) {
        PartyMusicSheet(MUSIC_OPTIONS.picks, PARTY_MIX.pickId, onPick = {}, onClose = {})
    }

    @Test
    fun musicSheetLight() = sheet("party_settings_music_sheet_light", false) {
        PartyMusicSheet(MUSIC_OPTIONS.picks, PARTY_MIX.pickId, onPick = {}, onClose = {})
    }
}
