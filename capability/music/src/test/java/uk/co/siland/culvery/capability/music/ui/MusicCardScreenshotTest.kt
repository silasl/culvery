package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The Home card in the right column's cell as the grid gives it on the 1280×800 canvas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class MusicCardScreenshotTest {
    /** The right-hand Home cell; app's HomeGridTest pins the same size. */
    private val rightHalfSize = DpSize(701.dp, 285.5.dp)
    @get:Rule val compose = createComposeRule()
    private val kitchen = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG, volume = 30, artUrl = "art")

    private fun snap(name: String, dark: Boolean, pick: HomePick?, status: String? = null) = compose.snap(name, dark, Modifier.padding(16.dp)) {
        Box(Modifier.size(rightHalfSize)) { MusicCard(pick, status, null, GroupActions.None) }
    }

    @Test fun playingDark() = snap("music_card_playing_dark", true, HomePick(kitchen, false))

    @Test fun playingLight() = snap("music_card_playing_light", false, HomePick(kitchen, false))

    @Test
    fun radioDark() = snap("music_card_radio_dark", true, HomePick(group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, artUrl = "art"), false))

    @Test fun pausedDark() = snap("music_card_paused_dark", true, HomePick(kitchen.copy(state = PlayState.PAUSED), false))

    @Test fun nothingDark() = snap("music_card_nothing_dark", true, HomePick(group("b", "Bedroom"), nothingPlaying = true))

    @Test
    fun problemDark() = snap("music_card_problem_dark", true, null, status = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.")

    @Test fun lookingDark() = snap("music_card_looking_dark", true, null)

    /** Play, Next and Shuffle everything share one row with the label whole. Fails if the row squeezes Shuffle. */
    @Test
    fun shuffleEverythingFitsBesidePlayAndNext() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Column {
                    Box(Modifier.size(rightHalfSize)) { MusicCard(HomePick(kitchen.copy(state = PlayState.PAUSED), nothingPlaying = true), null, null, GroupActions.None) }
                    Box(Modifier.wrapContentSize()) { ShuffleButton(kitchen, "free", ButtonSurface.Surf, GroupActions.None, compact = true) }
                }
            }
        }
        val inCard = compose.onNodeWithTag("music_card_shuffle").getUnclippedBoundsInRoot()
        val free = compose.onNodeWithTag("free_shuffle").getUnclippedBoundsInRoot()
        assertThat((inCard.right - inCard.left).value).isWithin(0.01f).of((free.right - free.left).value)
        assertThat((inCard.bottom - inCard.top).value).isAtLeast(48f)
    }
}
