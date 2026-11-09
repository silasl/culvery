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

    /** Fails if any of the 32 entries drifts from the instructor's list and his answers, apostrophes included. */
    @Test
    fun everyMoveMatchesTheInstructorsList() {
        val list = listOf<List<String?>>(
            listOf("Start", "Narani junbi sogi", "Parallel ready stance", "Feet parallel, shoulder width apart; calm and ready to begin."),
            listOf("1", "Niunja so sang sonkal makgi", "L-stance, twin knife-hand block", "Both knife-hands block at once, guarding the front and the side."),
            listOf("2", "Gunnun so sonkal nopunde ap taerigi", "Walking stance, high front knife-hand strike", "Strike the neck with the knife-hand while the other hand guards the forehead."),
            listOf("3", "Niunja so sonkal kaunde daebi makgi", "L-stance, middle knife-hand guarding block", "Turn and block the middle section, the rear hand guarding the chest."),
            listOf("4", "Gunnun so opun sonkut nopunde tulgi", "Walking stance, high flat fingertip thrust", "Fingertips thrust at the eyes, palm facing down."),
            listOf("5", "Niunja so sonkal kaunde daebi makgi", "L-stance, middle knife-hand guarding block", "Turn to face a new attacker and guard the middle section."),
            listOf("6", "Guburyo junbi sogi A", "Bending ready stance A", "Stand on one leg, chambered for the kick that follows."),
            listOf("7", "Kaunde yopcha jirugi", "Middle side piercing kick", "Drive the foot sword straight out to the side."),
            listOf("8", "Niunja so sonkal kaunde daebi makgi", "L-stance, middle knife-hand guarding block", "Land from the kick and guard the middle section."),
            listOf("9", "Twimyo yopcha jirugi, wen niunja so sonkal kaunde daebi makgi", "Flying side piercing kick, then L-stance knife-hand guarding block", "Jump and kick in the air, then land balanced and guard at once."),
            listOf("10", "Niunja so palmok najunde yop makgi", "L-stance, low forearm side block", "Sweep a low kick aside with the forearm."),
            listOf("11", null, "Walking stance, grab the opponent's head", "Reach up with both hands to seize the head."),
            listOf("12", "Moorup ollyo chagi", "Knee upward kick", "Pull the head down with both hands and drive the knee up into it."),
            listOf("13", "Gunnun so sonkal dung nopunde bandae ap taerigi", "Walking stance, high reverse ridge-hand strike", "The ridge of the hand swings in to the side of the head; the other hand rests under the elbow."),
            listOf("14", "Nopunde dollyo chagi", "High turning kick", "A circular kick to the head; flow straight into the next kick."),
            listOf("15", "Kaunde dwitcha jirugi", "Middle back piercing kick", "Drive the heel straight back into the body, fast after the turning kick."),
            listOf("16", "Niunja so palmok kaunde daebi makgi", "L-stance, middle forearm guarding block", "Guard the middle section with both forearms."),
            listOf("17", "Kaunde dollyo chagi", "Middle turning kick", "A circular kick to the body."),
            listOf("18", "Gojung so digutja makgi", "Fixed stance, U-shape block", "Both arms block high and low at once, as if stopping a staff."),
            listOf("19", "Twigi, wen niunja so sonkal kaunde daebi makgi", "Jump and spin, then L-stance knife-hand guarding block", "Jump and spin a full turn, landing on the same spot ready to guard."),
            listOf("20", "Gunnun so dwijibun sonkut najunde tulgi", "Walking stance, low upset fingertip thrust", "Fingertips thrust up into the lower body, palm facing up."),
            listOf("21", "Niunja so dung joomuk baro yopdwi taerigi wa palmok najunde bandae makgi", "L-stance, side back strike with the back fist and low block", "Strike behind you with the back fist while blocking low in front."),
            listOf("22", "Gunnun so sun sonkut kaunde tulgi", "Walking stance, middle straight fingertip thrust", "Straight fingertips thrust into the solar plexus."),
            listOf("23", "Gunnun so doo palmok nopunde makgi", "Walking stance, high double forearm block", "One forearm blocks high while the other backs it up."),
            listOf("24", "Annun so orun palmok kaunde ap makgi, orun dung joomuk nopunde yop taerigi", "Sitting stance, middle front block, then high side back-fist strike", "Block in front with the forearm, then snap the back fist out to the side of the head."),
            listOf("25", "Kaunde yopcha jirugi", "Middle side piercing kick, right foot", "The first of two side kicks along the same line."),
            listOf("26", "Kaunde yopcha jirugi", "Middle side piercing kick, left foot", "The second side kick, with the other leg."),
            listOf("27", "Niunja so kyocha sonkal momchau makgi", "L-stance, X knife-hand checking block", "Crossed knife-hands stop a kick before it lands."),
            listOf("28", "Gunnun so sang sonbadak ollyo makgi", "Walking stance, twin palm upward block", "Both palms push upward to block a rising attack."),
            listOf("29", "Gunnun so palmok chookyo makgi", "Walking stance, rising block", "Lift the forearm to block a strike to the head."),
            listOf("30", "Gunnun so kaunde bandae jirugi", "Walking stance, middle reverse punch", "The pattern ends with a left-hand punch, marking Admiral Yi's death in battle."),
            listOf("End", "Baro", "Return to ready posture", "Bring the left foot back to the ready posture."),
        )
        assertThat(CHOONG_MOO.moves.map { listOf(it.number, it.romanised, it.english, it.description) }).containsExactlyElementsIn(list).inOrder()
    }

    /** The instructor: move 11 has no Korean name and needs none. */
    @Test
    fun move11HasNoKoreanName() {
        val eleven = CHOONG_MOO.moves.first { it.number == "11" }
        assertThat(eleven.romanised).isNull()
        assertThat(eleven.hangul).isNull()
    }

    @Test
    fun endIsBaro() {
        val end = CHOONG_MOO.moves.first { it.number == "End" }
        assertThat(end.romanised).isEqualTo("Baro")
        assertThat(end.hangul).isEqualTo("바로")
    }

    /** Fails if move 21's 와 can break onto a line of its own, apart from the word before it. */
    @Test
    fun move21KeepsWaWithTheWordBefore() {
        val hangul = CHOONG_MOO.moves.first { it.number == "21" }.hangul!!
        assertThat(hangul).contains("때리기와 팔목")
        assertThat(hangul).doesNotContain(" 와 ")
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
