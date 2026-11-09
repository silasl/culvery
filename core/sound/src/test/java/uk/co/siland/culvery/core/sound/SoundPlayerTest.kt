package uk.co.siland.culvery.core.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private var io: CoroutineDispatcher = UnconfinedTestDispatcher()
    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }
    private val asked = mutableListOf<String>()
    private val made = mutableMapOf<String, MediaPlayer>()

    private enum class Player { STARTS, WONT_START }

    private fun tablet(name: String) = Sound("file:$name.wav", name, SoundSource.OnTablet(File("$name.wav")), null)

    private val classic = BuiltInSound.CLASSIC.asSound()
    private val organ = BuiltInSound.ORGAN.asSound()
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
        }, scope, io)
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
    fun theChimeIsANotificationEvent() = test {
        assertThat(CHIME_ATTRIBUTES.usage).isEqualTo(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        assertThat(CHIME_ATTRIBUTES.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    /** Fails if a sound opens on the caller's thread rather than the injected dispatcher. */
    @Test
    fun aFileOpensOnTheInjectedDispatcher() = test {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "sound-io-test") }
        io = executor.asCoroutineDispatcher()
        val openedOn = mutableListOf<String>()
        val callers = mutableListOf<String>()
        val player = SoundPlayer({ sound ->
            openedOn += Thread.currentThread().name.substringBefore(" @coroutine")
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)
        try {
            callers += Thread.currentThread().name.substringBefore(" @coroutine")
            assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STARTED)
            assertThat(player.playDoorbell(organ, 1f)).isTrue()
        } finally {
            executor.shutdown()
        }
        assertThat(openedOn).containsExactly("sound-io-test", "sound-io-test")
        assertThat(callers.single()).isNotEqualTo("sound-io-test")
    }

    /** Fails if a caller cancelled while its sound opens leaves the opened player neither released nor tracked. */
    @Test
    fun aCancelledCallerLeavesNoPlayerOpen() = test {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "sound-io-test") }
        val opening = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val player = SoundPlayer({ sound ->
            opening.countDown()
            gate.await()
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, executor.asCoroutineDispatcher())
        try {
            val job = launch(Dispatchers.Default) { player.playPad(1, honk, 1f) }
            opening.await()
            job.cancel()
            gate.countDown()
            job.join()
        } finally {
            executor.shutdown()
        }
        assertThat(player.live.size == 1 || released(honk)).isTrue()
    }

    @Test
    fun anOrganThatWontLoadPlaysTheChime() = test {
        val player = player(organ to null, classic to Player.STARTS)
        assertThat(player.playDoorbell(organ, 1f)).isTrue()
        assertThat(asked).containsExactly(organ.id, classic.id).inOrder()
    }

    @Test
    fun anOrganThatWontStartPlaysTheChime() = test {
        val player = player(organ to Player.WONT_START, classic to Player.STARTS)
        assertThat(player.playDoorbell(organ, 1f)).isTrue()
        assertThat(asked).containsExactly(organ.id, classic.id).inOrder()
        assertThat(player.live).hasSize(1)
    }

    @Test
    fun theOrganPlaysAloneWhenItWorks() = test {
        val player = player(organ to Player.STARTS, classic to Player.STARTS)
        assertThat(player.playDoorbell(organ, 1f)).isTrue()
        assertThat(asked).containsExactly(organ.id)
    }

    @Test
    fun theChimeFailingIsFalse() = test {
        val player = player()
        assertThat(player.playDoorbell(organ, 1f)).isFalse()
        assertThat(asked).containsExactly(organ.id, classic.id).inOrder()
        asked.clear()
        assertThat(player.playDoorbell(classic, 1f)).isFalse()
        assertThat(asked).containsExactly(classic.id)
    }

    @Test
    fun aBrokenDoorbellSoundRingsTheClassicChime() = test {
        val player = player(honk to Player.WONT_START, classic to Player.STARTS)
        assertThat(player.playDoorbell(honk, 1f)).isTrue()
        assertThat(asked).containsExactly(honk.id, classic.id).inOrder()
        assertThat(playing(classic)).isTrue()
    }

    @Test
    fun aPadPlaysAndReportsItself() = test {
        val player = player(honk to Player.STARTS)
        assertThat(player.padPlaying.value).isNull()
        assertThat(player.playPad(2, honk, 1f)).isEqualTo(PadStart.STARTED)
        assertThat(player.padPlaying.value).isEqualTo(2)
    }

    @Test
    fun aPreviewNamesItsSoundUntilItStops() = test {
        val player = player(honk to Player.STARTS)
        assertThat(player.playPad(2, honk, 1f)).isEqualTo(PadStart.STARTED)
        assertThat(player.previewing.value).isNull()
        assertThat(player.playPad(PREVIEW_PAD, honk, 1f)).isEqualTo(PadStart.STARTED)
        assertThat(player.previewing.value).isEqualTo(honk.id)
        player.stopPads()
        assertThat(player.previewing.value).isNull()
    }

    @Test
    fun aPreviewStopsAtTheCapItWasGiven() = test {
        val player = player(honk to Player.STARTS)
        player.playPad(PREVIEW_PAD, honk, 1f, SoundPlayer.DOORBELL_CAP_MS)
        passTime(SoundPlayer.PAD_CAP_MS + 1)
        assertThat(playing(honk)).isTrue()
        passTime(SoundPlayer.DOORBELL_CAP_MS - SoundPlayer.PAD_CAP_MS)
        assertThat(released(honk)).isTrue()
        assertThat(player.previewing.value).isNull()
    }

    @Test
    fun aPadClearsWhenItsSoundEnds() = test {
        val player = player(honk to Player.STARTS)
        player.playPad(2, honk, 1f)
        shadowOf(made.getValue(honk.id)).invokeCompletionListener()
        assertThat(player.padPlaying.value).isNull()
        assertThat(released(honk)).isTrue()
    }

    @Test
    fun aPadClearsWhenItsSoundErrors() = test {
        val player = player(honk to Player.STARTS)
        player.playPad(2, honk, 1f)
        shadowOf(made.getValue(honk.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertThat(player.padPlaying.value).isNull()
        assertThat(released(honk)).isTrue()
    }

    @Test
    fun aPadStopsTheLastPad() = test {
        val player = player(honk to Player.STARTS, howl to Player.STARTS)
        player.playPad(1, honk, 1f)
        assertThat(player.playPad(2, howl, 1f)).isEqualTo(PadStart.STARTED)
        assertThat(released(honk)).isTrue()
        assertThat(playing(howl)).isTrue()
        assertThat(player.padPlaying.value).isEqualTo(2)
    }

    @Test
    fun stopPadsStopsThePad() = test {
        val player = player(honk to Player.STARTS)
        player.playPad(1, honk, 1f)
        player.stopPads()
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }

    @Test
    fun aDoorbellStopsThePads() = test {
        val player = player(honk to Player.STARTS, classic to Player.STARTS)
        player.playPad(1, honk, 1f)
        assertThat(player.playDoorbell(classic, 1f)).isTrue()
        assertThat(player.padPlaying.value).isNull()
        assertThat(released(honk)).isTrue()
        assertThat(playing(classic)).isTrue()
    }

    @Test
    fun aBrokenPadSoundIsFalse() = test {
        val player = player(honk to null, howl to Player.WONT_START)
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.FAILED)
        assertThat(player.padPlaying.value).isNull()
        assertThat(player.playPad(1, howl, 1f)).isEqualTo(PadStart.FAILED)
        assertThat(player.padPlaying.value).isNull()
        assertThat(player.live).isEmpty()
    }

    @Test
    fun aBrokenPadStillStopsThePadBefore() = test {
        val player = player(honk to Player.STARTS, howl to null)
        player.playPad(1, honk, 1f)
        assertThat(player.playPad(2, howl, 1f)).isEqualTo(PadStart.FAILED)
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }

    @Test
    fun aTabletSoundStopsAtItsCap() = test {
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
    fun aBuiltInSoundHasNoCap() = test {
        val player = player(organ to Player.STARTS)
        player.playDoorbell(organ, 1f)
        passTime(SoundPlayer.DOORBELL_CAP_MS + 1)
        assertThat(playing(organ)).isTrue()
    }

    @Test
    fun aCapDoesNotStopTheNextSound() = test {
        val player = player(honk to Player.STARTS, howl to Player.STARTS)
        player.playPad(1, honk, 1f)
        passTime(5_000)
        player.playPad(2, howl, 1f)
        passTime(4_000)
        assertThat(playing(howl)).isTrue()
        assertThat(player.padPlaying.value).isEqualTo(2)
    }

    @Test
    fun aRingWhileAPadOpensLeavesThePadUnstarted() = test {
        lateinit var player: SoundPlayer
        var ringDuringOpen = true
        player = SoundPlayer({ sound ->
            if (sound.id == honk.id && ringDuringOpen) {
                ringDuringOpen = false
                runBlocking { player.playDoorbell(classic, 1f) }
            }
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STOPPED)
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
        assertThat(playing(classic)).isTrue()
    }

    @Test
    fun aRingStartingWhileAPadOpensLeavesThePadUnstarted() = test {
        lateinit var player: SoundPlayer
        player = SoundPlayer({ sound ->
            player.ringStarted()
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STOPPED)
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }

    /** Fails if a doorbell sound that errors while playing gets no chime, or the chime itself falls back again. */
    @Test
    fun aDoorbellSoundThatFailsMidwayFallsBackToTheChimeOnce() = test {
        val player = player(honk to Player.STARTS, classic to Player.STARTS)
        assertThat(player.playDoorbell(honk, 1f)).isTrue()
        shadowOf(made.getValue(honk.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        passTime(0)
        assertThat(asked).containsExactly(honk.id, classic.id).inOrder()
        assertThat(playing(classic)).isTrue()
        shadowOf(made.getValue(classic.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        passTime(0)
        assertThat(asked).containsExactly(honk.id, classic.id).inOrder()
    }

    /** Regression guard: passes today, as there is no fallback to cancel; it fails if one ignores a newer ring. */
    @Test
    fun noFallbackAfterANewerRing() = test {
        val player = player(honk to Player.STARTS, classic to Player.STARTS)
        player.playDoorbell(honk, 1f)
        player.ringStarted()
        shadowOf(made.getValue(honk.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        passTime(0)
        assertThat(asked).containsExactly(honk.id)
    }

    /** Fails if a pad whose open ends after a stop still starts, or the result is not STOPPED. */
    @Test
    fun aStopWhileAPadOpensKeepsItFromStarting() = test {
        lateinit var player: SoundPlayer
        player = SoundPlayer({ sound ->
            player.stopPads()
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STOPPED)
        assertThat(released(honk)).isTrue()
        assertThat(player.padPlaying.value).isNull()
    }

    /** Fails if a pad asked for while the doorbell sound opens starts, or is not refused as DOORBELL. */
    @Test
    fun aPadDoesNotStartWhileTheDoorbellOpens() = test {
        lateinit var player: SoundPlayer
        var padResult: PadStart? = null
        player = SoundPlayer({ sound ->
            asked += sound.id
            if (sound.id == classic.id) padResult = runBlocking { player.playPad(1, honk, 1f) }
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)
        assertThat(player.playDoorbell(classic, 1f)).isTrue()
        assertThat(padResult).isEqualTo(PadStart.DOORBELL)
        assertThat(asked).containsExactly(classic.id)
        assertThat(player.padPlaying.value).isNull()
    }

    /** Fails if a pad starts while the doorbell sound plays, or can not start once it ends. */
    @Test
    fun aPadDoesNotStartWhileTheDoorbellPlays() = test {
        val player = player(classic to Player.STARTS, honk to Player.STARTS)
        player.playDoorbell(classic, 1f)
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.DOORBELL)
        assertThat(asked).containsExactly(classic.id)
        shadowOf(made.getValue(classic.id)).invokeCompletionListener()
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STARTED)
    }

    /** Fails if a pad tapped between a doorbell sound's error and the chime's start plays, or the chime is lost. */
    @Test
    fun aPadTappedInTheGapAfterAnErrorDoesNotPlay() = test {
        val player = player(honk to Player.STARTS, howl to Player.STARTS, classic to Player.STARTS)
        player.playDoorbell(honk, 1f)
        shadowOf(made.getValue(honk.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)

        assertThat(player.playPad(1, howl, 1f)).isEqualTo(PadStart.DOORBELL)
        passTime(0)

        assertThat(playing(classic)).isTrue()
        assertThat(asked).doesNotContain(howl.id)
    }

    /** Fails if the chime starts although a ring came while it opened. */
    @Test
    fun aRingDuringTheFallbacksOpenGetsNoFallbackChime() = test {
        lateinit var player: SoundPlayer
        var ringDuringChime = false
        player = SoundPlayer({ sound ->
            if (sound.id == classic.id && ringDuringChime) player.ringStarted()
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)
        player.playDoorbell(honk, 1f)
        ringDuringChime = true
        shadowOf(made.getValue(honk.id)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        passTime(0)

        assertThat(released(classic)).isTrue()
        assertThat(player.live).isEmpty()
    }

    /** Fails if the first of two quick pad plays is not STOPPED, or the second does not play. */
    @Test
    fun theLastOfTwoQuickTapsPlays() = test {
        lateinit var player: SoundPlayer
        var second: PadStart? = null
        player = SoundPlayer({ sound ->
            if (sound.id == honk.id && second == null) second = runBlocking { player.playPad(2, howl, 1f) }
            prepared(sound.id).also { made[sound.id] = it }
        }, scope, io)

        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STOPPED)

        assertThat(second).isEqualTo(PadStart.STARTED)
        assertThat(player.padPlaying.value).isEqualTo(2)
        assertThat(released(honk)).isTrue()
    }

    /** Fails if a built-in doorbell sound that never reports its end keeps the pads out past the ceiling. */
    @Test
    fun aBuiltInDoorbellThatNeverEndsIsEndedAtTheCeiling() = test {
        val player = player(organ to Player.STARTS, honk to Player.STARTS)
        player.playDoorbell(organ, 1f)
        passTime(SoundPlayer.DOORBELL_CEILING_MS - 1)
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.DOORBELL)
        passTime(1)

        assertThat(released(organ)).isTrue()
        assertThat(player.playPad(1, honk, 1f)).isEqualTo(PadStart.STARTED)
    }
}
