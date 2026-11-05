package uk.co.siland.culvery.core.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.State
import org.robolectric.shadows.util.DataSource

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SoundPlayerTest {
    private val scope = TestScope(StandardTestDispatcher())
    private val asked = mutableListOf<String>()
    private val made = mutableMapOf<String, MediaPlayer>()

    private enum class Player { STARTS, WONT_START }

    private fun builtIn(sound: BuiltInSound) = Sound(sound.id, sound.label, SoundSource.BuiltIn(sound), sound.theme)

    private fun tablet(name: String) = Sound("file:$name.wav", name, SoundSource.OnTablet(File("$name.wav")), null)

    private val classic = builtIn(BuiltInSound.CLASSIC)
    private val organ = builtIn(BuiltInSound.ORGAN)
    private val honk = tablet("Test Honk")
    private val howl = tablet("Test Howl")

    /** Hands back, per sound, a player that starts, one whose start throws, as a real player in the error state does, or null. */
    private fun player(vararg players: Pair<Sound, Player?>): SoundPlayer {
        val by = players.associate { (sound, kind) -> sound.id to kind }
        return SoundPlayer({ sound ->
            asked += sound.id
            when (by[sound.id]) {
                Player.STARTS -> prepared(sound.id).also { made[sound.id] = it }
                Player.WONT_START -> object : MediaPlayer() {
                    override fun start() = throw IllegalStateException("error state")
                }
                null -> null
            }
        }, scope)
    }

    private fun prepared(source: String): MediaPlayer {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(source), ShadowMediaPlayer.MediaInfo(30_000, 0))
        return MediaPlayer().also {
            it.setDataSource(source)
            it.prepare()
        }
    }

    private fun released(sound: Sound) = shadowOf(made.getValue(sound.id)).state == State.END

    private fun playing(sound: Sound) = shadowOf(made.getValue(sound.id)).state == State.STARTED

    private fun passTime(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun theChimeIsANotificationEvent() {
        assertThat(CHIME_ATTRIBUTES.usage).isEqualTo(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        assertThat(CHIME_ATTRIBUTES.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    @Test
    fun anOrganThatWontLoadPlaysTheChime() {
        val player = player(organ to null, classic to Player.STARTS)
        assertThat(player.playDoorbell(organ, 1f)).isTrue()
        assertThat(asked).containsExactly(organ.id, classic.id).inOrder()
    }

    @Test
    fun anOrganThatWontStartPlaysTheChime() {
        val player = player(organ to Player.WONT_START, classic to Player.STARTS)
        assertThat(player.playDoorbell(organ, 1f)).isTrue()
        assertThat(asked).containsExactly(organ.id, classic.id).inOrder()
        assertThat(player.live).hasSize(1)
    }

    @Test
    fun theOrganPlaysAloneWhenItWorks() {
        val player = player(organ to Player.STARTS, classic to Player.STARTS)
        assertThat(player.playDoorbell(organ, 1f)).isTrue()
        assertThat(asked).containsExactly(organ.id)
    }

    @Test
    fun theChimeFailingIsFalse() {
        val player = player()
        assertThat(player.playDoorbell(organ, 1f)).isFalse()
        assertThat(asked).containsExactly(organ.id, classic.id).inOrder()
        asked.clear()
        assertThat(player.playDoorbell(classic, 1f)).isFalse()
        assertThat(asked).containsExactly(classic.id)
    }

    @Test
    fun aBrokenDoorbellSoundRingsTheClassicChime() {
        val player = player(honk to Player.WONT_START, classic to Player.STARTS)
        assertThat(player.playDoorbell(honk, 1f)).isTrue()
        assertThat(asked).containsExactly(honk.id, classic.id).inOrder()
        assertThat(playing(classic)).isTrue()
    }

    @Test
    fun aPadPlaysAndReportsItself() {
        val player = player(honk to Player.STARTS)
        assertThat(player.padPlaying.value).isNull()
        assertThat(player.playPad(2, honk, 1f)).isTrue()
        assertThat(player.padPlaying.value).isEqualTo(2)
    }

    @Test
    fun aPadClearsWhenItsSoundEnds() {
        val player = player(honk to Player.STARTS)
        player.playPad(2, honk, 1f)
        shadowOf(made.getValue(honk.id)).invokeCompletionListener()
        assertThat(player.padPlaying.value).isNull()
        assertThat(released(honk)).isTrue()
    }

    @Test
    fun aPadClearsWhenItsSoundErrors() {
        val player = player(honk to Player.STARTS)
        player.playPad(2, honk, 1f)
        shadowOf(made.getValue(honk.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertThat(player.padPlaying.value).isNull()
        assertThat(released(honk)).isTrue()
    }

    @Test
    fun aPadStopsTheLastPad() {
        val player = player(honk to Player.STARTS, howl to Player.STARTS)
        player.playPad(1, honk, 1f)
        assertThat(player.playPad(2, howl, 1f)).isTrue()
        assertThat(released(honk)).isTrue()
        assertThat(playing(howl)).isTrue()
        assertThat(player.padPlaying.value).isEqualTo(2)
    }

    @Test
    fun stopPadsStopsThePad() {
        val player = player(honk to Player.STARTS)
        player.playPad(1, honk, 1f)
        player.stopPads()
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }

    @Test
    fun aDoorbellStopsThePads() {
        val player = player(honk to Player.STARTS, classic to Player.STARTS)
        player.playPad(1, honk, 1f)
        assertThat(player.playDoorbell(classic, 1f)).isTrue()
        assertThat(player.padPlaying.value).isNull()
        assertThat(released(honk)).isTrue()
        assertThat(playing(classic)).isTrue()
    }

    @Test
    fun aBrokenPadSoundIsFalse() {
        val player = player(honk to null, howl to Player.WONT_START)
        assertThat(player.playPad(1, honk, 1f)).isFalse()
        assertThat(player.padPlaying.value).isNull()
        assertThat(player.playPad(1, howl, 1f)).isFalse()
        assertThat(player.padPlaying.value).isNull()
        assertThat(player.live).isEmpty()
    }

    @Test
    fun aBrokenPadStillStopsThePadBefore() {
        val player = player(honk to Player.STARTS, howl to null)
        player.playPad(1, honk, 1f)
        assertThat(player.playPad(2, howl, 1f)).isFalse()
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }

    @Test
    fun aTabletSoundStopsAtItsCap() {
        val player = player(honk to Player.STARTS, howl to Player.STARTS)
        player.playPad(1, honk, 1f)
        passTime(SoundPlayer.PAD_CAP_MS - 1)
        assertThat(playing(honk)).isTrue()
        passTime(1)
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()

        player.playDoorbell(howl, 1f)
        passTime(SoundPlayer.DOORBELL_CAP_MS - 1)
        assertThat(playing(howl)).isTrue()
        passTime(1)
        assertThat(released(howl)).isTrue()
        assertThat(player.live).isEmpty()
    }

    @Test
    fun aBuiltInSoundHasNoCap() {
        val player = player(organ to Player.STARTS)
        player.playDoorbell(organ, 1f)
        passTime(SoundPlayer.DOORBELL_CAP_MS + 1)
        assertThat(playing(organ)).isTrue()
    }

    @Test
    fun aCapDoesNotStopTheNextSound() {
        val player = player(honk to Player.STARTS, howl to Player.STARTS)
        player.playPad(1, honk, 1f)
        passTime(5_000)
        player.playPad(2, howl, 1f)
        passTime(4_000)
        assertThat(playing(howl)).isTrue()
        assertThat(player.padPlaying.value).isEqualTo(2)
    }

    @Test
    fun aRingWhileAPadOpensLeavesThePadUnstarted() {
        lateinit var player: SoundPlayer
        var ringDuringOpen = true
        player = SoundPlayer({ sound ->
            if (sound.id == honk.id && ringDuringOpen) {
                ringDuringOpen = false
                player.playDoorbell(classic, 1f)
            }
            prepared(sound.id).also { made[sound.id] = it }
        }, scope)
        assertThat(player.playPad(1, honk, 1f)).isFalse()
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
        assertThat(playing(classic)).isTrue()
    }

    @Test
    fun aRingStartingWhileAPadOpensLeavesThePadUnstarted() {
        lateinit var player: SoundPlayer
        player = SoundPlayer({ sound ->
            player.ringStarted()
            prepared(sound.id).also { made[sound.id] = it }
        }, scope)
        assertThat(player.playPad(1, honk, 1f)).isFalse()
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }
}
