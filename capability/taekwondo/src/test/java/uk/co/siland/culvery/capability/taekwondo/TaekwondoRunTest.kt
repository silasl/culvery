package uk.co.siland.culvery.capability.taekwondo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** Time is virtual: a say takes one second, so a move spoken at 10 s ends at 11 s. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TaekwondoRunTest {
    private class Rig(scope: TestScope, hasKorean: Boolean = true) {
        val speaker = FakeSpeaker(hasKorean) { scope.testScheduler.currentTime }
        val run = TaekwondoRun(speaker, scope.backgroundScope)
        val states = mutableListOf<RunState>()

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { run.state.collect { states += it } }
        }
    }

    private suspend fun TestScope.passes(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun pattern(vararg numbers: String) = Pattern("Test", numbers.map { n -> CHOONG_MOO.moves.first { it.number == n } })

    @Test
    fun countsDownTenThenSaysTheFirstMove() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false)
        passes(9_999)
        assertThat(rig.states).containsExactlyElementsIn(listOf(RunState.Idle) + (10 downTo 1).map { RunState.Counting(it) }).inOrder()
        assertThat(rig.speaker.calls).isEmpty()

        passes(1)
        assertThat(rig.states.last()).isEqualTo(RunState.Moving(0, CHOONG_MOO.moves[0]))
        assertThat(rig.speaker.calls).containsExactly("나란히 준비 서기" to true)
        assertThat(rig.speaker.times).containsExactly(10_000L)
    }

    @Test
    fun detailedAddsTheDescriptionInEnglish() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = true)
        passes(17_001)

        assertThat(rig.speaker.calls.take(4)).containsExactly(
            "나란히 준비 서기" to true,
            "Feet parallel, shoulder width apart; calm and ready to begin." to false,
            "니은자 서 쌍 손칼 막기" to true,
            "Both knife-hands block at once, guarding the front and the side." to false,
        ).inOrder()
    }

    @Test
    fun notDetailedReadsOnlyTheTerm() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false)
        passes(17_000)

        assertThat(rig.speaker.calls).containsExactly("나란히 준비 서기" to true, "니은자 서 쌍 손칼 막기" to true).inOrder()
    }

    @Test
    fun threeSecondsOfQuietBetweenMoves() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false)
        passes(30_000)

        // Spoken at 10 s for 1 s, quiet until 14 s, then the next.
        assertThat(rig.speaker.times.take(3)).containsExactly(10_000L, 14_000L, 18_000L).inOrder()
    }

    @Test
    fun stepSixSpeaksWithoutTheA() = runTest {
        val rig = Rig(this)

        rig.run.start(pattern("6"), detailed = false)
        passes(10_000)

        assertThat(rig.states.last()).isEqualTo(RunState.Moving(0, move("6")))
        assertThat(rig.speaker.calls).containsExactly("구부려 준비 서기" to true)
    }

    @Test
    fun afterTheLastTenSecondsThenTheFirstAgain() = runTest {
        val rig = Rig(this)

        rig.run.start(pattern("Start", "1"), detailed = false)
        // The last move is spoken at 14 s, so it ends at 15 s with no quiet after it.
        passes(14_999)
        assertThat(rig.states.last()).isEqualTo(RunState.Moving(1, move("1")))
        passes(1)
        assertThat(rig.states.last()).isEqualTo(RunState.Again(10))

        passes(9_000)
        assertThat(rig.states.last()).isEqualTo(RunState.Again(1))
        passes(1_000)
        assertThat(rig.states.last()).isEqualTo(RunState.Moving(0, move("Start")))
        assertThat(rig.speaker.times).containsExactly(10_000L, 14_000L, 25_000L).inOrder()
        assertThat(rig.states.filterIsInstance<RunState.Again>().map { it.seconds }).containsExactlyElementsIn((10 downTo 1).toList()).inOrder()
    }

    @Test
    fun noKoreanVoiceReadsTheRomanisedInEnglish() = runTest {
        val rig = Rig(this, hasKorean = false)

        rig.run.start(CHOONG_MOO, detailed = false)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly("Narani junbi sogi" to false)
    }

    @Test
    fun noHangulReadsTheRomanisedInEnglish() = runTest {
        val rig = Rig(this)

        rig.run.start(pattern("11"), detailed = false)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly("Gunnun so, mori japgi?" to false)
    }

    @Test
    fun stopEndsAtOnceAndStopsTheSpeaker() = runTest {
        val rig = Rig(this)
        rig.run.start(CHOONG_MOO, detailed = true)
        passes(10_500)

        rig.run.stop()

        assertThat(rig.run.state.value).isEqualTo(RunState.Idle)
        assertThat(rig.speaker.stops).isEqualTo(1)
        passes(60_000)
        assertThat(rig.speaker.calls).hasSize(1)
        assertThat(rig.run.state.value).isEqualTo(RunState.Idle)
    }

    @Test
    fun aRunStartedAgainAfterAStopCountsFromTen() = runTest {
        val rig = Rig(this)
        rig.run.start(CHOONG_MOO, detailed = false)
        passes(12_000)
        rig.run.stop()

        rig.run.start(CHOONG_MOO, detailed = false)

        assertThat(rig.run.state.value).isEqualTo(RunState.Counting(10))
    }

    @Test
    fun onlyStartAndStopAreLogged() = runTest {
        ShadowLog.clear()
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = true)
        passes(20_000)
        rig.run.stop()

        assertThat(ShadowLog.getLogs().map { it.msg }).containsExactly("A run started", "A run stopped").inOrder()
    }
}
