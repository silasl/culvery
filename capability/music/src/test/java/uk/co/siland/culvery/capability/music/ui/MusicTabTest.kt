package uk.co.siland.culvery.capability.music.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.TestMusicSystem
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.TestMusicProvider
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.capability.music.recordingGroupActions
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.waitFor

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

    private fun showTab(status: String? = null, lines: Map<String, String> = emptyMap(), groups: List<SpeakerGroup> = listOf(lounge, kitchen)) = compose.setContent {
        var shown by remember { mutableStateOf(emptySet<String>()) }
        CulveryTheme(dark = true) {
            MusicTab(groups, status, lines, shown, { id -> shown = if (id in shown) shown - id else shown + id }, actions)
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

    /** 9 §4.5: only a group of several has the chip; it opens a two-column grid of the rooms' volumes. */
    @Test
    fun theGroupChipSaysHowManyRoomsAndOpensTheirVolumes() {
        val pair = group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 35)
        showTab(groups = listOf(pair, kitchen))
        compose.onNodeWithTag("music_row_k_rooms").assertDoesNotExist()
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertDoesNotExist()
        compose.onNodeWithTag("music_row_l_rooms").assertTextEquals("2 rooms").assert(notSelected).performClick()
        compose.onNodeWithTag("music_row_l_rooms").assertIsSelected()
        val lounge = compose.onNodeWithTag("music_row_l_room_Lounge-id_volume").getUnclippedBoundsInRoot()
        val dining = compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").getUnclippedBoundsInRoot()
        assertThat(dining.top).isEqualTo(lounge.top)
        assertThat(dining.left).isGreaterThan(lounge.right)
        assertThat(lounge.height.value).isWithin(1f).of(48f)
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume_slider")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Dining room volume")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "35 percent"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        assertThat(calls).containsExactly("room Dining room-id 20")
        compose.assertAccessible()
        compose.onNodeWithTag("music_row_l_rooms").performClick()
        compose.onNodeWithTag("music_row_l_rooms").assert(notSelected)
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertDoesNotExist()
    }

    /** A button chip holding no value says nothing about selection until it is open. */
    private val notSelected = SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected)

    private fun sizeOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { Math.round(it.width.value) to Math.round(it.height.value) }

    @Test
    fun theHeaderCountsRoomsAndGroups() {
        showTab(groups = listOf(lounge, kitchen, group("b", "Bedroom")))
        compose.onNodeWithText("Music").assertIsDisplayed()
        compose.onNodeWithText("4 rooms · 1 group · Sonos").assertIsDisplayed()
        compose.assertAccessible()
    }

    /** One 15 dp gap under the title block, and one under a status line. */
    @Test
    fun theFirstCardSitsOneGapBelowTheHeader() {
        showTab()
        val subtitle = compose.onNodeWithText("3 rooms · 1 group · Sonos").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("music_row_l").getUnclippedBoundsInRoot()
        assertThat(card.top.value - subtitle.bottom.value).isWithin(1f).of(15f)
    }

    @Test
    fun aStatusLineSitsOneGapBelowTheHeaderAndAboveTheFirstCard() {
        showTab(status = "The tablet is offline. Check the Wi-Fi.")
        val subtitle = compose.onNodeWithText("3 rooms · 1 group · Sonos").getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("music_status").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("music_row_l").getUnclippedBoundsInRoot()
        assertThat(status.top.value - subtitle.bottom.value).isWithin(1f).of(15f)
        assertThat(card.top.value - status.bottom.value).isWithin(1f).of(15f)
    }

    @Test
    fun oneRoomAloneHasNoGroupCount() {
        showTab(groups = listOf(kitchen))
        compose.onNodeWithText("1 room · Sonos").assertIsDisplayed()
        compose.assertAccessible()
    }

    @Test
    fun noSonosShowsUnderTheNewTitle() {
        showTab(status = "The tablet is offline. Check the Wi-Fi.", groups = emptyList())
        val title = compose.onNodeWithText("Music").getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("music_status").getUnclippedBoundsInRoot()
        assertThat(status.top).isAtLeast(title.bottom)
        compose.onNodeWithText("Sonos", substring = true).assertDoesNotExist()
        compose.assertAccessible()
    }

    @Test
    fun eachCardHasTheControlRowAndALabelledVolume() {
        showTab()
        assertThat(sizeOf("music_row_l_pause")).isEqualTo(48 to 48)
        assertThat(sizeOf("music_row_l_next")).isEqualTo(48 to 48)
        assertThat(sizeOf("music_row_l_volume")).isEqualTo(280 to 48)
        compose.onNodeWithTag("music_row_l_volume").assert(hasAnyDescendant(hasText("40")))
        compose.onNodeWithTag("music_row_l_volume_slider")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Lounge + Dining room volume")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "40 percent"))
        assertThat(sizeOf("music_row_l_now_art")).isEqualTo(80 to 80)
        compose.assertAccessible()
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

    /** The tab over a fake system whose groups a test changes. */
    private fun hostOver(groups: List<SpeakerGroup>): TestMusicSystem {
        val settings = music.settings
        val provider = TestMusicProvider()
        val speakers = Speakers(setOf(provider), settings, scope)
        runBlocking {
            settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
            withTimeout(5_000) { speakers.system.first { it != null } }
        }
        val live = provider.systems.single()
        live.groups.value = groups
        val checks = MusicChecks(speakers.system, MutableStateFlow(true), scope).also { it.start() }
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
                    MusicTabHost(MusicView(speakers, settings, scope), MusicActions({ live }, scope), checks)
                }
            }
        }
        return live
    }

    /** Review focus: a group splitting under an open grid closes it without a crash, and a group that re-forms starts closed. */
    @Test
    fun aGroupThatGoesClosesItsRooms() {
        val system = hostOver(listOf(lounge, kitchen))
        compose.onNodeWithTag("music_row_l_rooms").performClick()
        compose.onNodeWithTag("music_row_l_room_Dining room-id_volume").assertExists()
        system.groups.value = listOf(group("l", "Lounge", state = PlayState.PLAYING, nowPlaying = STATION), group("d", "Dining room"), kitchen)
        compose.waitFor { compose.onAllNodesWithTag("music_row_d").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("music_row_l_room_Dining room-id_volume").assertCountEquals(0)
        compose.onNodeWithTag("music_row_l_rooms").assertDoesNotExist()
        system.groups.value = listOf(lounge, kitchen)
        compose.waitFor { compose.onAllNodesWithTag("music_row_l_rooms").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("music_row_l_rooms").assert(notSelected)
        compose.onAllNodesWithTag("music_row_l_room_Dining room-id_volume").assertCountEquals(0)
    }

    /** Pin: Play last is the same onPlay as the round Play, so a refusal shows the row's usual line. */
    @Test
    fun aRefusedPlayLastShowsOnItsRow() {
        val bedroom = group("b", "Bedroom")
        hostOver(listOf(bedroom, kitchen)).failures["play b"] = MusicRefusedException(701)
        compose.onNodeWithTag("music_row_b_play").performClick()
        compose.waitFor { compose.onAllNodesWithTag("music_row_b_line").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("music_row_b_line").assertTextEquals("Sonos didn’t do that. Try again.")
    }

    /** 9 §4.5: stopped with nothing loaded, a room offers Play last and no Next. */
    @Test
    fun aQuietRoomOffersPlayLastAndNoNext() {
        showTab(groups = listOf(lounge, kitchen, group("b", "Bedroom")))
        compose.onNodeWithText("Play last", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("music_row_b_play").assertContentDescriptionEquals("Play last in Bedroom").performClick()
        assertThat(calls).containsExactly("play b")
        compose.onNodeWithTag("music_row_b_next").assertDoesNotExist()
        compose.onNodeWithTag("music_row_b_pause").assertDoesNotExist()
        compose.onNodeWithText("Nothing playing", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("music_row_b_now_art", useUnmergedTree = true).assertExists()
        compose.assertAccessible()
    }

    /** Pin: a paused room with a song keeps the round Play and Next. */
    @Test
    fun aPausedRoomWithATrackStillHasPlayAndNext() {
        showTab()
        compose.onNodeWithTag("music_row_k_play").assertContentDescriptionEquals("Play Kitchen")
        compose.onNodeWithTag("music_row_k_next").assertIsEnabled()
        compose.onNodeWithContentDescription("Kitchen, paused").assertExists()
        compose.onNodeWithText("Song", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Play last").assertDoesNotExist()
    }
}
