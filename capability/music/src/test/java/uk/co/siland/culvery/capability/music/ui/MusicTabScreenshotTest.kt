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
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.FavouritesLoad
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.group

/** The tab in the shell's content area on the 1280×800 canvas; the sheet against the right edge, as the shell places it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicTabScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val groups = listOf(
        group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 40, artUrl = "art"),
        group("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = SONG, volume = 30, artUrl = "art"),
        group("b", "Bedroom"),
    )

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) = compose.snap(name, dark, Modifier.size(1_116.dp, 700.dp), content)

    private fun tab(name: String, dark: Boolean, status: String? = null, roomsShown: Set<String> = emptySet(), shown: List<SpeakerGroup> = groups) =
        snap(name, dark) { MusicTab(shown, status, emptyMap(), roomsShown, {}, GroupActions.None) }

    @Test fun tabDark() = tab("music_tab_dark", true)

    @Test fun tabLight() = tab("music_tab_light", false)

    @Test
    fun tabQuietDark() = tab(
        "music_tab_quiet_dark", true,
        shown = listOf(group("b", "Bedroom"), group("k", "Kitchen", state = PlayState.STOPPED, nowPlaying = SONG, artUrl = "art"), group("g", "Garden", "Hall", "Study")),
    )

    @Test fun tabRoomsDark() = tab("music_tab_rooms_dark", true, roomsShown = setOf("l"))

    @Test
    fun tabStatusDark() = tab("music_tab_status_dark", true, status = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.")

    private fun sheet(name: String, load: FavouritesLoad, dark: Boolean = true) = snap(name, dark) {
        Box(Modifier.size(1_116.dp, 700.dp), contentAlignment = Alignment.CenterEnd) { FavouritesSheet("Kitchen", load, null, {}, {}) }
    }

    @Test
    fun sheetDark() = sheet(
        "favourites_sheet_dark",
        FavouritesLoad.Ready(
            listOf(
                Favourite("FV:2/1", "Culvery FM", "art", FavouriteKind.STREAM),
                Favourite("FV:2/2", "Sunday morning", "art", FavouriteKind.QUEUED),
                Favourite("FV:2/3", "Party mix", null, FavouriteKind.QUEUED),
            ),
        ),
    )

    private val withPlaylists = FavouritesLoad.Ready(
        listOf(
            Favourite("FV:2/1", "Culvery FM", "art", FavouriteKind.STREAM),
            Favourite("FV:2/2", "Sunday morning", "art", FavouriteKind.QUEUED),
        ),
        listOf(
            Favourite("SQ:0", "Party mode", "art", FavouriteKind.QUEUED),
            Favourite("SQ:5", "Rainy day", null, FavouriteKind.QUEUED),
        ),
    )

    @Test fun sheetWithPlaylistsDark() = sheet("favourites_sheet_playlists_dark", withPlaylists)

    @Test fun sheetWithPlaylistsLight() = sheet("favourites_sheet_playlists_light", withPlaylists, dark = false)

    @Test fun sheetEmptyDark() = sheet("favourites_sheet_empty_dark", FavouritesLoad.Ready(emptyList()))
}
