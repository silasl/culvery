package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 7 §4.3: about one group command a second, ten light commands a second, the newest waiting command winning. */
class HuePacerTest {
    private val sent = mutableListOf<Pair<String, Long>>()

    private fun TestScope.pacer() = HuePacer(now = { currentTime })

    private fun TestScope.send(pacer: HuePacer, target: String, kind: PaceKind, label: String = target) =
        async { pacer.send(target, kind) { sent += label to currentTime } }

    /** Fails if two group commands go less than a second apart, or the second waits longer than it must. */
    @Test
    fun groupCommandsAreASecondApart() = runTest {
        val pacer = pacer()
        send(pacer, "gl-lounge", PaceKind.GROUP)
        send(pacer, "gl-kitchen", PaceKind.GROUP)
        runCurrent()
        advanceTimeBy(999)
        assertThat(sent).containsExactly("gl-lounge" to 0L)
        advanceTimeBy(1)
        runCurrent()
        assertThat(sent).containsExactly("gl-lounge" to 0L, "gl-kitchen" to 1_000L).inOrder()
    }

    /** Fails if two light commands go less than 100 ms apart, or the second waits longer than it must. */
    @Test
    fun lightCommandsAreAHundredMillisecondsApart() = runTest {
        val pacer = pacer()
        send(pacer, "light-1", PaceKind.LIGHT)
        send(pacer, "light-2", PaceKind.LIGHT)
        runCurrent()
        advanceTimeBy(99)
        assertThat(sent).containsExactly("light-1" to 0L)
        advanceTimeBy(1)
        runCurrent()
        assertThat(sent).containsExactly("light-1" to 0L, "light-2" to 100L).inOrder()
    }

    /** Fails if the two kinds share one gap, so a light waits behind a room's second. */
    @Test
    fun aLightDoesNotWaitForARoom() = runTest {
        val pacer = pacer()
        send(pacer, "gl-lounge", PaceKind.GROUP)
        send(pacer, "light-1", PaceKind.LIGHT)
        runCurrent()
        assertThat(sent).containsExactly("gl-lounge" to 0L, "light-1" to 0L)
    }

    /** Fails if waiting commands for one room aren't replaced, so the bridge sees more than its guidance (Review Focus 4). */
    @Test
    fun twentyTogglesInASecondAreAtMostTwoSends() = runTest {
        val pacer = pacer()
        val answers = (0 until 20).map { i ->
            async {
                delay(i * 50L)
                pacer.send("gl-lounge", PaceKind.GROUP) { sent += "toggle $i" to currentTime }
            }
        }
        advanceUntilIdle()
        assertThat(sent).containsExactly("toggle 0" to 0L, "toggle 19" to 1_000L).inOrder()
        val results = answers.map { it.await() }
        assertThat(results.first()).isTrue()
        assertThat(results.last()).isTrue()
        assertThat(results.subList(1, 19)).containsExactlyElementsIn(List(18) { false })
    }

    /** Fails if an older waiting command runs instead of the newer one, or another room's waiting command is dropped. */
    @Test
    fun theNewestWaitingCommandWins() = runTest {
        val pacer = pacer()
        send(pacer, "gl-lounge", PaceKind.GROUP, "lounge on")
        runCurrent()
        val off = send(pacer, "gl-lounge", PaceKind.GROUP, "lounge off")
        val kitchen = send(pacer, "gl-kitchen", PaceKind.GROUP, "kitchen")
        runCurrent()
        val dim = send(pacer, "gl-lounge", PaceKind.GROUP, "lounge 40%")
        runCurrent()
        // The replaced caller hears at once, not when its turn would have come.
        assertThat(off.isCompleted).isTrue()
        assertThat(off.await()).isFalse()
        advanceUntilIdle()
        assertThat(kitchen.await()).isTrue()
        assertThat(dim.await()).isTrue()
        assertThat(sent.map { it.first }).containsExactly("lounge on", "kitchen", "lounge 40%").inOrder()
    }

    /** Fails if a queued room command survives dropWaiting, so a queued "on" undoes All lights off (Review Focus 4). */
    @Test
    fun dropWaitingLetsNoQueuedCommandFollow() = runTest {
        val pacer = pacer()
        send(pacer, "gl-kitchen", PaceKind.GROUP)
        val light = send(pacer, "light-1", PaceKind.LIGHT)
        runCurrent()
        val lounge = send(pacer, "gl-lounge", PaceKind.GROUP)
        val light2 = send(pacer, "light-2", PaceKind.LIGHT)
        runCurrent()
        pacer.dropWaiting()
        runCurrent()
        assertThat(lounge.isCompleted).isTrue()
        assertThat(lounge.await()).isFalse()
        assertThat(light2.await()).isFalse()
        assertThat(light.await()).isTrue()
        // The pacer still sends what comes after, at the next turn.
        val home = send(pacer, "gl-home", PaceKind.GROUP)
        advanceUntilIdle()
        assertThat(home.await()).isTrue()
        assertThat(sent).containsExactly("gl-kitchen" to 0L, "light-1" to 0L, "gl-home" to 1_000L).inOrder()
    }

    /** Fails if a failed command keeps its turn's lock, so every later command for that kind hangs. */
    @Test
    fun aFailedCommandLetsTheNextOneGo() = runTest {
        val pacer = pacer()
        val failed = async { runCatching { pacer.send("gl-lounge", PaceKind.GROUP) { error("refused") } } }
        runCurrent()
        assertThat(failed.await().isFailure).isTrue()
        val next = send(pacer, "gl-lounge", PaceKind.GROUP)
        advanceUntilIdle()
        assertThat(next.await()).isTrue()
        assertThat(sent).containsExactly("gl-lounge" to 1_000L)
    }
}
