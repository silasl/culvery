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
    private class Rig(scope: TestScope, hasKorean: Boolean? = true) {
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

        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)
        passes(9_999)
        assertThat(rig.states).containsExactlyElementsIn(listOf(RunState.Idle) + (10 downTo 1).map { RunState.Counting(it) }).inOrder()
        assertThat(rig.speaker.calls).isEmpty()

        passes(1)
        assertThat(rig.states.last()).isEqualTo(RunState.Moving(0, CHOONG_MOO.moves[0]))
        assertThat(rig.speaker.calls).containsExactly("나란히 준비 서기" to true)
        assertThat(rig.speaker.times).containsExactly(10_000L)
    }

    @Test
    fun detailedAddsTheEnglishNameAndDescription() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = true, Language.KOREAN)
        passes(18_001)

        assertThat(rig.speaker.calls.take(6)).containsExactly(
            "나란히 준비 서기" to true,
            "Parallel ready stance" to false,
            "Feet parallel, shoulder width apart; calm and ready to begin." to false,
            "니은자 서 쌍 손칼 막기" to true,
            "L-stance, twin knife-hand block" to false,
            "Both knife-hands block at once, guarding the front and the side." to false,
        ).inOrder()
    }

    @Test
    fun notDetailedReadsOnlyTheTerm() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)
        passes(17_000)

        assertThat(rig.speaker.calls).containsExactly("나란히 준비 서기" to true, "니은자 서 쌍 손칼 막기" to true).inOrder()
    }

    @Test
    fun threeSecondsOfQuietBetweenMoves() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)
        passes(30_000)

        // Spoken at 10 s for 1 s, quiet until 14 s, then the next.
        assertThat(rig.speaker.times.take(3)).containsExactly(10_000L, 14_000L, 18_000L).inOrder()
    }

    @Test
    fun aMoveWithNoKoreanNameIsReadInEnglish() = runTest {
        val plain = Rig(this)
        plain.run.start(pattern("11"), detailed = false, Language.KOREAN)
        passes(10_000)
        assertThat(plain.speaker.calls).containsExactly("Walking stance, grab the opponent's head" to false)
        plain.run.stop()

        val detailed = Rig(this)
        detailed.run.start(pattern("11"), detailed = true, Language.KOREAN)
        passes(12_000)
        assertThat(detailed.speaker.calls).containsExactly(
            "Walking stance, grab the opponent's head" to false,
            "Reach up with both hands to seize the head." to false,
        ).inOrder()
    }

    @Test
    fun stepSixSpeaksWithoutTheA() = runTest {
        val rig = Rig(this)

        rig.run.start(pattern("6"), detailed = false, Language.KOREAN)
        passes(10_000)

        assertThat(rig.states.last()).isEqualTo(RunState.Moving(0, move("6")))
        assertThat(rig.speaker.calls).containsExactly("구부려 준비 서기" to true)
    }

    @Test
    fun afterTheLastTenSecondsThenTheFirstAgain() = runTest {
        val rig = Rig(this)

        rig.run.start(pattern("Start", "1"), detailed = false, Language.KOREAN)
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
    fun englishReadsTheEnglishName() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false, Language.ENGLISH)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly("Parallel ready stance" to false)
    }

    @Test
    fun englishInDetailedModeReadsTheNameOnceThenTheDescription() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = true, Language.ENGLISH)
        passes(12_000)

        assertThat(rig.speaker.calls).containsExactly(
            "Parallel ready stance" to false,
            "Feet parallel, shoulder width apart; calm and ready to begin." to false,
        ).inOrder()
    }

    @Test
    fun phoneticReadsTheRespellingInTheEnglishVoice() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = false, Language.PHONETIC)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly(move("Start").phonetic!! to false)
    }

    @Test
    fun phoneticInDetailedModeAddsTheEnglishNameAndDescription() = runTest {
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = true, Language.PHONETIC)
        passes(13_000)

        assertThat(rig.speaker.calls).containsExactly(
            move("Start").phonetic!! to false,
            "Parallel ready stance" to false,
            "Feet parallel, shoulder width apart; calm and ready to begin." to false,
        ).inOrder()
    }

    @Test
    fun aMoveWithNoKoreanNameIsReadInEnglishWhateverTheLanguage() = runTest {
        Language.entries.forEach { language ->
            val rig = Rig(this)
            rig.run.start(pattern("11"), detailed = false, language)
            passes(10_000)
            assertThat(rig.speaker.calls).containsExactly("Walking stance, grab the opponent's head" to false)
            rig.run.stop()
        }
    }

    @Test
    fun koreanWithNoKoreanVoiceReadsThePhonetic() = runTest {
        val rig = Rig(this, hasKorean = false)

        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly(move("Start").phonetic!! to false)
    }

    @Test
    fun koreanWithAVoiceNotKnownYetReadsThePhonetic() = runTest {
        val rig = Rig(this, hasKorean = null)

        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly(move("Start").phonetic!! to false)
    }

    @Test
    fun noHangulReadsThePhonetic() = runTest {
        val rig = Rig(this)

        rig.run.start(Pattern("Test", listOf(move("1").copy(hangul = null, spokenHangul = null))), detailed = false, Language.KOREAN)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly(move("1").phonetic!! to false)
    }

    @Test
    fun noRespellingReadsTheRomanisedInEnglish() = runTest {
        val rig = Rig(this)

        rig.run.start(Pattern("Test", listOf(move("1").copy(phonetic = null))), detailed = false, Language.PHONETIC)
        passes(10_000)

        assertThat(rig.speaker.calls).containsExactly("Niunja so sang sonkal makgi" to false)
    }

    @Test
    fun stopEndsAtOnceAndStopsTheSpeaker() = runTest {
        val rig = Rig(this)
        rig.run.start(CHOONG_MOO, detailed = true, Language.KOREAN)
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
        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)
        passes(12_000)
        rig.run.stop()

        rig.run.start(CHOONG_MOO, detailed = false, Language.KOREAN)

        assertThat(rig.run.state.value).isEqualTo(RunState.Counting(10))
    }

    @Test
    fun onlyStartAndStopAreLogged() = runTest {
        ShadowLog.clear()
        val rig = Rig(this)

        rig.run.start(CHOONG_MOO, detailed = true, Language.KOREAN)
        passes(20_000)
        rig.run.stop()

        assertThat(ShadowLog.getLogs().map { it.msg }).containsExactly("A run started", "A run stopped").inOrder()
    }
}
