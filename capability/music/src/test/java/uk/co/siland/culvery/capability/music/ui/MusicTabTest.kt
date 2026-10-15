package uk.co.siland.culvery.capability.music.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicFixture
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.TestMusicProvider
import uk.co.siland.culvery.capability.music.TestOverlay
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.capability.music.recordingGroupActions
import uk.co.siland.culvery.capability.music.waitFor
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 6 §5.2, §5.5: one row per group, playing first; the Rooms chip; the status line; Favourites; the tab reads the layout. */
@RunWith(AndroidJUnit4::class)
class MusicTabTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val music = MusicFixture()
    private val scope = music.scope
    private val calls = mutableListOf<String>()
    private val actions = recordingGroupActions(calls)
    private val lounge = group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 40)
    private val kitchen = group("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = SONG)

    private fun showTab(status: String? = null, lines: Map<String, String> = emptyMap()) = compose.setContent {
        var shown by remember { mutableStateOf(emptySet<String>()) }
        CulveryTheme(dark = true) {
            MusicTab(listOf(lounge, kitchen), status, lines, shown, { id -> shown = if (id in shown) shown - id else shown + id }, actions)
        }
    }

    @Test
    fun eachGroupIsARowWithItsControlsAndWords() {
        showTab()
        compose.onNodeWithContentDescription("Lounge + Dining room, playing Culvery FM, The Breakfast Show").assertExists()
        compose.onNodeWithContentDescription("Kitchen, paused").assertExists()
        compose.onNodeWithTag("music_row_l_next").assertIsNotEnabled()
        compose.onNodeWithTag("music_row_k_next").assertIsEnabled()
        compose.onNodeWithTag("music_row_l_pause").assertContentDescriptionEquals("Pause Lounge + Dining room")
        compose.onNodeWithTag("music_row_k_play").performClick()
        // 6 §5.2: Favourites and Shuffle everything always shown here, playing or not.
        compose.onNodeWithTag("music_row_l_shuffle").assertContentDescriptionEquals("Shuffle everything in Lounge + Dining room")
        compose.onNodeWithTag("music_row_k_favourites").assertContentDescriptionEquals("Favourites for Kitchen").performClick()
        assertThat(calls).containsExactly("play k", "favourites k").inOrder()
        compose.assertAccessible()
    }

    /** 6 §5.2: only a grouped row has Rooms; it shows each room's own slider. */
    @Test
    fun theRoomsChipShowsEachRoomsVolume() {
        showTab()
        compose.onNodeWithTag("music_row_k_rooms").assertDoesNotExist()
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertDoesNotExist()
        compose.onNodeWithTag("music_row_l_rooms").performClick()
        compose.onNodeWithContentDescription("Dining room volume").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        assertThat(calls).containsExactly("room Dining room-id 20")
        compose.onNodeWithTag("music_row_l_rooms").performClick()
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertDoesNotExist()
    }

    @Test
    fun theStatusLineShowsOnlyWhenSomethingIsWrong() {
        showTab(status = "The tablet is offline. Check the Wi-Fi.")
        compose.onNodeWithTag("music_status").assertExists()
    }

    @Test
    fun noStatusLineWhileAllIsWell() {
        showTab()
        compose.onNodeWithTag("music_status").assertDoesNotExist()
    }

    @Test
    fun aRefusedCommandShowsOnItsRow() {
        showTab(lines = mapOf("k" to "Sonos didn’t do that. Try again."))
        compose.onNodeWithTag("music_row_k_line").assertTextEquals("Sonos didn’t do that. Try again.")
        compose.onNodeWithTag("music_row_l_line").assertDoesNotExist()
    }

    /**
     * 6 §4.2, §5.2: with Home already watching (its layout read, the next not due for 30 s), opening the tab reads the
     * layout at once; the tab orders playing first and opens Favourites on the sheet.
     */
    @Test
    fun theTabReadsTheLayoutAsItOpensOrdersAndOpensFavourites() {
        val settings = music.settings
        val provider = TestMusicProvider()
        val speakers = Speakers(setOf(provider), settings, scope)
        runBlocking {
            settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
            withTimeout(5_000) { speakers.system.first { it != null } }
        }
        val system = provider.systems.single()
        system.groups.value = listOf(kitchen, lounge)
        val checks = MusicChecks(speakers.system, MutableStateFlow(true), scope).also { it.start() }
        checks.watch()
        runBlocking { withTimeout(5_000) { while ("checkLayout" !in system.calls) delay(10) } }
        system.calls.clear()
        val overlay = TestOverlay()
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    MusicTabHost(MusicView(speakers, settings, scope), MusicActions({ system }, scope), checks)
                }
            }
        }
        compose.waitFor { "checkLayout" in system.calls }
        assertThat(checks.watching).isEqualTo(2)
        val loungeTop = compose.onNodeWithTag("music_row_l").getUnclippedBoundsInRoot().top
        val kitchenTop = compose.onNodeWithTag("music_row_k").getUnclippedBoundsInRoot().top
        assertThat(loungeTop).isLessThan(kitchenTop)
        compose.onNodeWithTag("music_row_k_favourites").performClick()
        assertThat(overlay.shown).isEqualTo(1)
    }
}
