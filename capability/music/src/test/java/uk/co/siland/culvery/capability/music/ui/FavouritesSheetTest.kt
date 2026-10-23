package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.FavouritesLoad
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.TestMusicSystem
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 6 §5.3, §6: the household's Favourites; a tap plays one in the row's group and closes; one that can't play says so. */
@RunWith(AndroidJUnit4::class)
class FavouritesSheetTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val kitchen = group("k", "Kitchen")
    private val station = Favourite("FV:2/1", "Culvery FM", null, FavouriteKind.STREAM)
    private val playlist = Favourite("FV:2/2", "Sunday morning", null, FavouriteKind.QUEUED)
    private val system = TestMusicSystem(listOf(kitchen)).apply { favouriteList = listOf(station, playlist) }
    private var closed = 0

    @After
    fun tearDown() = scope.cancel()

    private fun host() = compose.setContent {
        CulveryTheme(dark = true) { FavouritesSheetHost(kitchen, MusicActions({ system }, scope)) { closed++ } }
    }

    @Test
    fun aTapPlaysTheFavouriteInTheGroupAndCloses() {
        host()
        compose.waitFor { "favourites" in system.calls }
        compose.onNodeWithText("Culvery FM").performClick()
        compose.waitFor { closed == 1 }
        assertThat(system.calls).contains("playFavourite k FV:2/1")
    }

    /** 6 §6: "Sonos couldn’t play this here." and the sheet stays open. */
    @Test
    fun oneThatCanNotPlayHereSaysSoAndStays() {
        system.failures["playFavourite k FV:2/2"] = MusicRefusedException(714)
        host()
        compose.waitFor { "favourites" in system.calls }
        compose.onNodeWithText("Sunday morning").performClick()
        compose.waitFor { "playFavourite k FV:2/2" in system.calls }
        compose.waitForIdle()
        compose.onNodeWithTag("favourites_line").assertTextEquals("Sonos couldn’t play this here.")
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun noneSaysHowToAddThem() {
        compose.setContent { CulveryTheme(dark = true) { FavouritesSheet("Kitchen", FavouritesLoad.Ready(emptyList()), null, {}, {}) } }
        compose.onNodeWithTag("favourites_empty").assertTextEquals("No Sonos Favourites or playlists yet. Add them in the Sonos app.")
        compose.onNodeWithContentDescription("Playlists").assertDoesNotExist()
    }

    private val party = Favourite("SQ:0", "Party mode", null, FavouriteKind.QUEUED)

    private fun show(load: FavouritesLoad, onPick: (Favourite) -> Unit = {}) =
        compose.setContent { CulveryTheme(dark = true) { FavouritesSheet("Kitchen", load, null, onPick, {}) } }

    @Test
    fun playlistsFollowTheFavouritesUnderTheirOwnHeading() {
        show(FavouritesLoad.Ready(listOf(station, playlist), listOf(party)))
        compose.onNodeWithText("Favourites and playlists").assertExists()
        val top = { tag: String -> compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top }
        val heading = compose.onNodeWithContentDescription("Playlists").fetchSemanticsNode().boundsInRoot.top
        assertThat(top("favourite_FV:2/2")).isLessThan(heading)
        assertThat(heading).isLessThan(top("playlist_SQ:0"))
        compose.onNodeWithTag("favourites_empty").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noPlaylistsLeavesNoHeading() {
        show(FavouritesLoad.Ready(listOf(station), emptyList()))
        compose.onNodeWithText("Culvery FM").assertExists()
        compose.onNodeWithContentDescription("Playlists").assertDoesNotExist()
    }

    @Test
    fun playlistsAloneAreShownWithoutTheEmptyLine() {
        show(FavouritesLoad.Ready(emptyList(), listOf(party)))
        compose.onNodeWithContentDescription("Playlists").assertExists()
        compose.onNodeWithText("Party mode").assertExists()
        compose.onNodeWithTag("favourites_empty").assertDoesNotExist()
    }

    @Test
    fun aTapOnAPlaylistPicksIt() {
        val picked = mutableListOf<Favourite>()
        show(FavouritesLoad.Ready(listOf(station), listOf(party)), picked::add)
        compose.onNodeWithTag("playlist_SQ:0").performClick()
        assertThat(picked).containsExactly(party)
    }

    @Test
    fun aFailedReadSaysWhy() {
        compose.setContent { CulveryTheme(dark = true) { FavouritesSheet("Kitchen", FavouritesLoad.Failed("The tablet is offline. Check the Wi-Fi."), null, {}, {}) } }
        compose.onNodeWithText("The tablet is offline. Check the Wi-Fi.").assertExists()
    }
}
