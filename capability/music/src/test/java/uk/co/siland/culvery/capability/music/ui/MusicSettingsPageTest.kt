package uk.co.siland.culvery.capability.music.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.MusicConnect
import uk.co.siland.culvery.capability.music.MusicFixture
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.TestMusicProvider
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.waitFor

/** 6 §5.4: Sonos, the usual room and the doorbell's share. */
@RunWith(AndroidJUnit4::class)
class MusicSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val music = MusicFixture()
    private val calls = mutableListOf<String>()
    private val actions = MusicSettingsActions(
        onFind = { calls += "find" },
        onEnterAddress = { calls += "address" },
        onUsualRoom = { calls += "usual $it" },
        onLowerTo = { calls += "lower $it" },
    )
    private val connected = connectedSettings

    private fun show(ui: MusicSettingsUi) = compose.setContent { CulveryTheme(dark = true) { InSettingsPane { MusicSettingsScreen(ui, actions) } } }

    @Test
    fun connectedShowsTheRoomsAndTheShare() {
        show(connected)
        compose.onNodeWithTag("music_connection").assertTextEquals("Connected.")
        compose.onNodeWithTag("music_usual_RINCON_A").assertIsSelected()
        compose.onNodeWithTag("music_usual_RINCON_B").performClick()
        compose.onNodeWithTag("music_lower_to")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("When the doorbell rings, lower music to")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 % of its volume"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("0 % (muted)").assertExists()
        assertThat(calls).containsExactly("usual RINCON_B", "lower 0").inOrder()
        compose.assertAccessible()
    }

    /** 6 §5.4: 0 % mutes. */
    @Test
    fun noShareSaysMuted() {
        show(connected.copy(lowerTo = 0))
        compose.onNodeWithTag("music_lower_to").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 % (muted)"))
    }

    @Test
    fun notConnectedSaysWhatToDo() {
        show(connected.copy(connected = false, rooms = emptyList(), usualRoomId = null))
        compose.onNodeWithTag("music_connection").assertTextEquals("Not connected yet. Find Sonos, or enter a speaker’s address.")
        compose.onNodeWithText("The rooms appear here once Sonos is found.").assertExists()
        compose.onNodeWithTag("music_find").performClick()
        compose.onNodeWithTag("music_address").performClick()
        assertThat(calls).containsExactly("find", "address").inOrder()
    }

    @Test
    fun findingSaysSoAndWaits() {
        show(connected.copy(finding = true))
        compose.onNodeWithTag("music_find").assertTextEquals("Looking for Sonos…").assertIsNotEnabled()
    }

    /** 6 §6: the same line as the tab and the card. */
    @Test
    fun aProblemShowsItsLine() {
        show(connected.copy(status = "Culvery found a different Sonos system. Find Sonos again in Settings › Music."))
        compose.onNodeWithText("Culvery found a different Sonos system. Find Sonos again in Settings › Music.").assertExists()
        compose.onNodeWithTag("music_connection").assertDoesNotExist()
    }

    @Test
    fun aFindThatFoundNothingSaysWhy() {
        show(connected.copy(connected = false, findLine = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."))
        compose.onNodeWithTag("music_find_line").assertTextEquals("No Sonos speakers answered. Check they’re on, or enter a speaker’s address.")
    }

    /** The sheet sends what was typed and shows the line it gets back; nothing is sent while the field is empty. */
    @Test
    fun theAddressSheetSendsWhatWasTypedAndSaysWhyNot() {
        val sent = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = true) {
                AddressSheet(onConnect = {
                    sent += it
                    "Type the address as four numbers, like 192.168.1.20."
                }, onEdited = {}, onClose = {})
            }
        }
        compose.onNodeWithTag("music_address_connect").assertIsNotEnabled()
        compose.onNodeWithTag("music_address_field").performTextInput("192.168.1.20:1400")
        compose.onNodeWithTag("music_address_connect").performClick()
        compose.waitFor { sent.isNotEmpty() }
        compose.onNodeWithTag("music_address_line").assertTextEquals("Type the address as four numbers, like 192.168.1.20.")
        assertThat(sent).containsExactly("192.168.1.20:1400")
    }

    /** A Find that found nothing stops being the page's line once a typed address connects. */
    @Test
    fun connectingByAddressClearsTheFindLine() {
        val settings = music.settings
        val scope = music.scope
        val sonos = TestMusicProvider().apply { speakers["192.168.1.20"] = MusicLink("Sonos_home", "192.168.1.20:1400") }
        val speakers = Speakers(setOf(sonos), settings, scope)
        val toaster = RecordingToaster()
        val overlay = TestOverlay()
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    InSettingsPane {
                        MusicSettingsHost(settings, speakers, MusicView(speakers, settings, scope), MusicConnect(sonos) { id, link -> settings.connect(id, link) }, toaster)
                    }
                    overlay.content?.invoke()
                }
            }
        }
        compose.waitFor { compose.onAllNodesWithTag("music_find").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("music_find").performClick()
        compose.waitFor { compose.onAllNodesWithText("No Sonos speakers answered. Check they’re on, or enter a speaker’s address.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("music_address").performClick()
        compose.onNodeWithTag("music_address_field").performTextInput("192.168.1.20")
        compose.onNodeWithTag("music_address_connect").performClick()
        compose.waitFor { compose.onAllNodesWithText("Connected.").fetchSemanticsNodes().isNotEmpty() }
        assertThat(toaster.shown).containsExactly("Connected to Sonos.")
        assertThat(overlay.content).isNull()
        compose.onNodeWithTag("music_find_line").assertTextEquals("")
    }
}
