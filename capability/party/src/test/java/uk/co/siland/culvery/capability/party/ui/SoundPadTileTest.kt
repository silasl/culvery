package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.BIRTHDAY_SONG
import uk.co.siland.culvery.capability.party.FakePlayer
import uk.co.siland.culvery.capability.party.GateRig
import uk.co.siland.culvery.capability.party.HONK
import uk.co.siland.culvery.capability.party.ORGAN
import uk.co.siland.culvery.capability.party.PadBlock
import uk.co.siland.culvery.capability.party.PadTaps
import uk.co.siland.culvery.capability.party.PadUi
import uk.co.siland.culvery.capability.party.TROMBONE
import uk.co.siland.culvery.core.ui.Birthday
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTrayGrid
import uk.co.siland.culvery.core.ui.TrayCell
import uk.co.siland.culvery.core.ui.test.TRAY_HEIGHT_DP
import uk.co.siland.culvery.core.ui.test.TRAY_WIDTH_DP
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.snap
import uk.co.siland.culvery.core.ui.test.waitFor

/** 18 §4: the sound pads in the One-tap tray's last row, at the tray's 333 dp. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class SoundPadTileTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(Job())

    @After fun stop() = scope.cancel()

    private val three = listOf(PadUi(1, TROMBONE), PadUi(2, ORGAN), PadUi(3, HONK))

    /** The row of [pads], the first carrying [line]; [spare] empty spots stand where Taekwondo's button would. */
    private fun row(name: String, dark: Boolean, pads: List<PadUi>, playing: Int? = null, blocked: Boolean = false, line: String? = null, spare: Int = 0, birthday: Birthday? = null) =
        compose.snap(name, dark, Modifier.padding(16.dp), birthday = birthday) {
            val cells = pads.mapIndexed { i, pad ->
                TrayCell("sound.pad${pad.pad}", line.takeIf { i == 0 }) { SoundPadTile(pad, pad.pad == playing, blocked) {} }
            } + List(spare) { TrayCell("spare$it") {} }
            HhTrayGrid("One-tap", listOf(cells), Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp))
        }

    @Test fun threePadsDark() = row("sound_pads_three_dark", true, three)

    @Test fun threePadsLight() = row("sound_pads_three_light", false, three)

    @Test fun twoPadsBesideTaekwondoDark() = row("sound_pads_two_dark", true, three.take(2), spare = 1)

    @Test fun onePlayingDark() = row("sound_pads_playing_dark", true, three, playing = 2)

    @Test fun nightDark() = row("sound_pads_night_dark", true, three, blocked = true, line = PadBlock.NIGHT.line)

    @Test fun nightLight() = row("sound_pads_night_light", false, three, blocked = true, line = PadBlock.NIGHT.line)

    @Test
    fun birthdayDark() = row("sound_pads_birthday_dark", true, listOf(PadUi(1, BIRTHDAY_SONG)) + three.drop(1), birthday = Birthday(0xFFE0457BL, listOf("Test Person")))

    private val player = FakePlayer()

    private fun host(rig: GateRig, pads: List<PadUi> = three) {
        val taps = PadTaps(player::play, player::stop, player.playing, rig.gate, { 1f }, scope)
        compose.setContent {
            CulveryTheme(dark = true) {
                HhTrayGrid(
                    "One-tap",
                    listOf(pads.map { pad -> TrayCell("sound.pad${pad.pad}") { SoundPadTileHost(pad, taps) } }),
                    Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
                )
            }
        }
    }

    /** 18 §4.4: fails if tapping the playing pad doesn't stop it, plays again, or tapping another doesn't play that one. */
    @Test
    fun tappingThePlayingPadStopsIt() {
        host(GateRig())
        player.playing.value = 1

        compose.onNodeWithTag("sound_pad_1").performClick()
        compose.waitFor { player.stops == 1 }
        compose.onNodeWithTag("sound_pad_2").performClick()
        compose.waitFor { player.plays.isNotEmpty() }

        assertThat(player.plays.map { it.first }).containsExactly(2)
        assertThat(player.stops).isEqualTo(1)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 18 §7: fails if a greyed pad can be tapped or plays. */
    @Test
    fun aBlockedPadDoesNotPlay() {
        val rig = GateRig()
        rig.night = true
        host(rig)

        compose.waitFor { runCatching { compose.onNodeWithTag("sound_pad_1").assertIsNotEnabled() }.isSuccess }
        compose.onNodeWithTag("sound_pad_1").performClick()
        compose.waitForIdle()

        assertThat(player.plays).isEmpty()
    }

    /** Fix round 1: fails if the playing pad can't be tapped to stop once a block starts, or the others stop being greyed. */
    @Test
    fun aPlayingPadStillStopsWhileBlocked() {
        val rig = GateRig()
        rig.house.holiday.value = true
        player.playing.value = 1
        host(rig)

        compose.waitFor { runCatching { compose.onNodeWithTag("sound_pad_2").assertIsNotEnabled() }.isSuccess }
        compose.onNodeWithTag("sound_pad_1").assertIsEnabled().performClick()
        compose.waitFor { player.stops == 1 }

        assertThat(player.plays).isEmpty()
    }
}
