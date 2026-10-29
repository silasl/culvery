package uk.co.siland.culvery.capability.door

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(AndroidJUnit4::class)
class MediaPlayerChimeTest {
    private val asked = mutableListOf<DoorSound>()

    /** Hands back, per sound, a player that starts, one whose start throws, as a real player in the error state does, or null. */
    private fun chime(vararg players: Pair<DoorSound, Player?>): MediaPlayerChime {
        val by = players.toMap()
        return MediaPlayerChime { sound ->
            asked += sound
            when (by[sound]) {
                Player.STARTS -> prepared()
                Player.WONT_START -> object : MediaPlayer() {
                    override fun start() = throw IllegalStateException("error state")
                }
                null -> null
            }
        }
    }

    private fun prepared(): MediaPlayer {
        val source = "chime"
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(source), ShadowMediaPlayer.MediaInfo(3_000, 0))
        return MediaPlayer().also {
            it.setDataSource(source)
            it.prepare()
        }
    }

    private enum class Player { STARTS, WONT_START }

    @Test
    fun anOrganThatWontLoadPlaysTheChime() {
        val chime = chime(DoorSound.ORGAN to null, DoorSound.CLASSIC to Player.STARTS)
        assertThat(chime.play(DoorSound.ORGAN, 1f)).isTrue()
        assertThat(asked).containsExactly(DoorSound.ORGAN, DoorSound.CLASSIC).inOrder()
    }

    @Test
    fun anOrganThatWontStartPlaysTheChime() {
        val chime = chime(DoorSound.ORGAN to Player.WONT_START, DoorSound.CLASSIC to Player.STARTS)
        assertThat(chime.play(DoorSound.ORGAN, 1f)).isTrue()
        assertThat(asked).containsExactly(DoorSound.ORGAN, DoorSound.CLASSIC).inOrder()
        assertThat(chime.live).hasSize(1)
    }

    @Test
    fun theOrganPlaysAloneWhenItWorks() {
        val chime = chime(DoorSound.ORGAN to Player.STARTS, DoorSound.CLASSIC to Player.STARTS)
        assertThat(chime.play(DoorSound.ORGAN, 1f)).isTrue()
        assertThat(asked).containsExactly(DoorSound.ORGAN)
    }

    @Test
    fun theChimeFailingIsFalse() {
        val chime = chime()
        assertThat(chime.play(DoorSound.ORGAN, 1f)).isFalse()
        assertThat(asked).containsExactly(DoorSound.ORGAN, DoorSound.CLASSIC).inOrder()
        asked.clear()
        assertThat(chime.play(DoorSound.CLASSIC, 1f)).isFalse()
        assertThat(asked).containsExactly(DoorSound.CLASSIC)
    }
}
