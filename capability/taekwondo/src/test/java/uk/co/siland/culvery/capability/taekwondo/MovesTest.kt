package uk.co.siland.culvery.capability.taekwondo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovesTest {
    /** Fails if a move is missing, doubled or out of order. */
    @Test
    fun choongMooHas32InOrder() {
        assertThat(CHOONG_MOO.name).isEqualTo("Choong-Moo")
        assertThat(CHOONG_MOO.moves.map { it.number }).containsExactlyElementsIn(listOf("Start") + (1..30).map { it.toString() } + "End").inOrder()
        assertThat(PATTERNS).containsExactly(CHOONG_MOO)
    }

    /** Fails if the text drifts from culvery-notes/taekwondo-choong-moo.html, apostrophes included. */
    @Test
    fun textIsTheInstructorsList() {
        fun move(number: String) = CHOONG_MOO.moves.first { it.number == number }.let { listOf(it.romanised, it.english, it.description) }
        assertThat(move("Start")).containsExactly(
            "Narani junbi sogi", "Parallel ready stance", "Feet parallel, shoulder width apart; calm and ready to begin.",
        ).inOrder()
        assertThat(move("11")).containsExactly(
            "Gunnun so, mori japgi?", "Walking stance, grab the opponent's head", "Reach up with both hands to seize the head.",
        ).inOrder()
        assertThat(move("21")).containsExactly(
            "Niunja so dung joomuk baro yopdwi taerigi wa palmok najunde bandae makgi",
            "L-stance, side back strike with the back fist and low block",
            "Strike behind you with the back fist while blocking low in front.",
        ).inOrder()
        assertThat(move("30")).containsExactly(
            "Gunnun so kaunde bandae jirugi", "Walking stance, middle reverse punch",
            "The pattern ends with a left-hand punch, marking Admiral Yi's death in battle.",
        ).inOrder()
        assertThat(move("End")).containsExactly(
            "Baro?", "Return to ready posture", "Bring the left foot back to the ready posture.",
        ).inOrder()
    }

    /** Fails if another move has lost its Hangul, or 11 or End has been given a guess. */
    @Test
    fun onlyElevenAndEndWaitForHangul() {
        assertThat(CHOONG_MOO.moves.filter { it.hangul == null }.map { it.number }).containsExactly("11", "End")
    }

    /** Fails if step 6 shows or speaks differently from the plan: it shows the "A" and the Korean voice drops it. */
    @Test
    fun stepSixShowsItsAAndSpeaksWithoutIt() {
        val six = CHOONG_MOO.moves.first { it.number == "6" }
        assertThat(six.hangul).isEqualTo("구부려 준비 서기 A")
        assertThat(six.spokenHangul).isEqualTo("구부려 준비 서기")
    }

    /** 15b M6: fails if a move, the pattern or a run's state prints move text in its toString. */
    @Test
    fun toStringLeavesOutTheMoves() {
        val move = CHOONG_MOO.moves.first { it.number == "1" }
        val printed = listOf(move.toString(), CHOONG_MOO.toString(), RunState.Moving(1, move).toString())

        assertThat(printed).containsExactly("Move", "Pattern", "Moving(1)").inOrder()
    }
}
