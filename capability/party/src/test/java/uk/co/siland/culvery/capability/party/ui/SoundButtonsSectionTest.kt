package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.FakePlayer
import uk.co.siland.culvery.capability.party.GateRig
import uk.co.siland.culvery.capability.party.HONK
import uk.co.siland.culvery.capability.party.LIBRARY
import uk.co.siland.culvery.capability.party.MemoryStore
import uk.co.siland.culvery.capability.party.ORGAN
import uk.co.siland.culvery.capability.party.PadTaps
import uk.co.siland.culvery.capability.party.SoundPadsStore
import uk.co.siland.culvery.capability.party.TROMBONE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.id
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.waitFor

private const val GONE = "file:halloween/Test Howl.ogg"

/** 18 §5: Settings › Modes › Sound buttons. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoundButtonsSectionTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(Job())
    private val overlay = TestOverlay()
    private val player = FakePlayer()
    private val store = SoundPadsStore(MemoryStore())
    private val room = MutableStateFlow(3)
    private var refreshes = 0

    @After fun stop() = scope.cancel()

    private fun show(vararg pads: String?) {
        pads.forEachIndexed { i, id -> runBlocking { store.setPad(i + 1, id) } }
        val taps = PadTaps(player::play, player::stop, player.playing, GateRig().gate, { 0.6f }, scope, Dispatchers.Unconfined)
        val choices = flowOf(LIBRARY.filter { it.id != BuiltInSound.HAPPY_BIRTHDAY.id })
        val sounds = SoundButtons(store, room, choices, { refreshes++ }, taps)
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.size(960.dp, 1200.dp)) {
                        InSettingsPane { SoundButtonsSection(sounds, RecordingToaster()) }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.waitFor { exists("sound_pad_3") }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun saved() = runBlocking { store.pads.first() }

    /** Fails if a pad's row doesn't name its sound or None, hide Play for None, or the birthday note is missing. */
    @Test
    fun threePadsWithTheirSounds() {
        show(TROMBONE.id, ORGAN.id, null)

        compose.onNodeWithTag("sound_pad_1").assertTextContains("Pad 1").assertTextContains(TROMBONE.name)
        compose.onNodeWithTag("sound_pad_2").assertTextContains("Pad 2").assertTextContains(ORGAN.name)
        compose.onNodeWithTag("sound_pad_3").assertTextContains("Pad 3").assertTextContains("None")
        assertThat(exists("sound_pad_1_play")).isTrue()
        assertThat(exists("sound_pad_3_play")).isFalse()
        compose.onNodeWithTag("sound_pads_note").assertTextEquals("On a birthday, pad 1 plays Happy Birthday.")
        assertThat(refreshes).isEqualTo(1)
    }

    /** Fails if a pad past the tray's room shows its sound rather than saying when it shows. */
    @Test
    fun aWaitingPadSaysSo() {
        room.value = 2
        show(TROMBONE.id, ORGAN.id, HONK.id)

        compose.onNodeWithTag("sound_pad_3").assertTextContains("Shows when there’s room in the tray")
        compose.onNodeWithTag("sound_pad_2").assertTextContains(ORGAN.name)
    }

    /** Fails if choosing a sound doesn't store it for that pad, close the sheet, or line a tablet sound "On this tablet". */
    @Test
    fun choosingASoundStoresIt() {
        show(TROMBONE.id, ORGAN.id, null)

        compose.onNodeWithTag("sound_pad_3").performClick()
        compose.onNodeWithTag("sound_choice_${HONK.id}").assertTextContains("On this tablet")
        assertThat(exists("sound_choice_${BuiltInSound.HAPPY_BIRTHDAY.id}")).isFalse()
        compose.onNodeWithTag("sound_choice_${HONK.id}").performClick()

        compose.waitFor { saved()[2] == HONK.id }
        assertThat(saved()).containsExactly(TROMBONE.id, ORGAN.id, HONK.id).inOrder()
        assertThat(exists("sound_pad_sheet")).isFalse()
        compose.waitFor { exists("sound_pad_3_play") }
    }

    /** Fails if choosing None doesn't store None for that pad. */
    @Test
    fun choosingNoneStoresNone() {
        show(TROMBONE.id, ORGAN.id, null)

        compose.onNodeWithTag("sound_pad_1").performClick()
        compose.onNodeWithTag("sound_choice_none").performClick()

        compose.waitFor { saved()[0] == null }
        assertThat(saved()).containsExactly(null, ORGAN.id, null).inOrder()
        compose.waitFor { !exists("sound_pad_1_play") }
    }

    /** Fails if a pad whose file has gone doesn't say so under the file's name, or still offers Play. */
    @Test
    fun aGoneFileSaysSo() {
        show(TROMBONE.id, GONE, null)

        compose.onNodeWithTag("sound_pad_2").assertTextContains("Test Howl · Not on this tablet any more")
        assertThat(exists("sound_pad_2_play")).isFalse()
    }

    /** Fails if Play doesn't play that pad's sound as the preview, at the doorbell's volume. */
    @Test
    fun playPlaysThePreview() {
        show(TROMBONE.id, ORGAN.id, null)

        compose.onNodeWithTag("sound_pad_2_play").performClick()

        compose.waitFor { player.plays.isNotEmpty() }
        assertThat(player.plays).containsExactly(0 to 0.6f)
    }
}
