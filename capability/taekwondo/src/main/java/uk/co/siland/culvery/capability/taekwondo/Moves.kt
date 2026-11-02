package uk.co.siland.culvery.capability.taekwondo

/** [hangul] null until the instructor fills it in; the English voice reads [romanised] then (15b §8). [toString] leaves out the text. */
data class Move(
    val number: String,
    val romanised: String,
    val hangul: String?,
    val english: String,
    val description: String,
    /** What the Korean voice says, when it differs from what's shown (step 6 drops its "A"). */
    val spokenHangul: String? = hangul,
) {
    override fun toString() = "Move"
}

/** [toString] leaves out the name and the moves. */
data class Pattern(val name: String, val moves: List<Move>) {
    override fun toString() = "Pattern"
}

/**
 * The text of `culvery-notes/taekwondo-choong-moo.html`, straight apostrophes included. Every Hangul entry is the plan's
 * transliteration of the romanised ITF term and is unchecked, for the instructor.
 */
val CHOONG_MOO = Pattern(
    "Choong-Moo",
    listOf(
    Move("Start", "Narani junbi sogi", "나란히 준비 서기", "Parallel ready stance",
        "Feet parallel, shoulder width apart; calm and ready to begin."),
    Move("1", "Niunja so sang sonkal makgi", "니은자 서 쌍 손칼 막기", "L-stance, twin knife-hand block",
        "Both knife-hands block at once, guarding the front and the side."),
    Move("2", "Gunnun so sonkal nopunde ap taerigi", "걷는 서 손칼 높은데 앞 때리기", "Walking stance, high front knife-hand strike",
        "Strike the neck with the knife-hand while the other hand guards the forehead."),
    Move("3", "Niunja so sonkal kaunde daebi makgi", "니은자 서 손칼 가운데 대비 막기", "L-stance, middle knife-hand guarding block",
        "Turn and block the middle section, the rear hand guarding the chest."),
    Move("4", "Gunnun so opun sonkut nopunde tulgi", "걷는 서 엎은 손끝 높은데 뚫기", "Walking stance, high flat fingertip thrust",
        "Fingertips thrust at the eyes, palm facing down."),
    Move("5", "Niunja so sonkal kaunde daebi makgi", "니은자 서 손칼 가운데 대비 막기", "L-stance, middle knife-hand guarding block",
        "Turn to face a new attacker and guard the middle section."),
    Move("6", "Guburyo junbi sogi A", "구부려 준비 서기 A", "Bending ready stance A",
        "Stand on one leg, chambered for the kick that follows.", spokenHangul = "구부려 준비 서기"),
    Move("7", "Kaunde yopcha jirugi", "가운데 옆차 지르기", "Middle side piercing kick",
        "Drive the foot sword straight out to the side."),
    Move("8", "Niunja so sonkal kaunde daebi makgi", "니은자 서 손칼 가운데 대비 막기", "L-stance, middle knife-hand guarding block",
        "Land from the kick and guard the middle section."),
    Move("9", "Twimyo yopcha jirugi, wen niunja so sonkal kaunde daebi makgi", "뛰며 옆차 지르기, 왼 니은자 서 손칼 가운데 대비 막기", "Flying side piercing kick, then L-stance knife-hand guarding block",
        "Jump and kick in the air, then land balanced and guard at once."),
    Move("10", "Niunja so palmok najunde yop makgi", "니은자 서 팔목 낮은데 옆 막기", "L-stance, low forearm side block",
        "Sweep a low kick aside with the forearm."),
    Move("11", "Gunnun so, mori japgi?", null, "Walking stance, grab the opponent's head",
        "Reach up with both hands to seize the head."),
    Move("12", "Moorup ollyo chagi", "무릎 올려 차기", "Knee upward kick",
        "Pull the head down with both hands and drive the knee up into it."),
    Move("13", "Gunnun so sonkal dung nopunde bandae ap taerigi", "걷는 서 손칼등 높은데 반대 앞 때리기", "Walking stance, high reverse ridge-hand strike",
        "The ridge of the hand swings in to the side of the head; the other hand rests under the elbow."),
    Move("14", "Nopunde dollyo chagi", "높은데 돌려 차기", "High turning kick",
        "A circular kick to the head; flow straight into the next kick."),
    Move("15", "Kaunde dwitcha jirugi", "가운데 뒤차 지르기", "Middle back piercing kick",
        "Drive the heel straight back into the body, fast after the turning kick."),
    Move("16", "Niunja so palmok kaunde daebi makgi", "니은자 서 팔목 가운데 대비 막기", "L-stance, middle forearm guarding block",
        "Guard the middle section with both forearms."),
    Move("17", "Kaunde dollyo chagi", "가운데 돌려 차기", "Middle turning kick",
        "A circular kick to the body."),
    Move("18", "Gojung so digutja makgi", "고정 서 디귿자 막기", "Fixed stance, U-shape block",
        "Both arms block high and low at once, as if stopping a staff."),
    Move("19", "Twigi, wen niunja so sonkal kaunde daebi makgi", "뛰기, 왼 니은자 서 손칼 가운데 대비 막기", "Jump and spin, then L-stance knife-hand guarding block",
        "Jump and spin a full turn, landing on the same spot ready to guard."),
    Move("20", "Gunnun so dwijibun sonkut najunde tulgi", "걷는 서 뒤집은 손끝 낮은데 뚫기", "Walking stance, low upset fingertip thrust",
        "Fingertips thrust up into the lower body, palm facing up."),
    Move("21", "Niunja so dung joomuk baro yopdwi taerigi wa palmok najunde bandae makgi", "니은자 서 등주먹 바로 옆뒤 때리기 와 팔목 낮은데 반대 막기", "L-stance, side back strike with the back fist and low block",
        "Strike behind you with the back fist while blocking low in front."),
    Move("22", "Gunnun so sun sonkut kaunde tulgi", "걷는 서 선 손끝 가운데 뚫기", "Walking stance, middle straight fingertip thrust",
        "Straight fingertips thrust into the solar plexus."),
    Move("23", "Gunnun so doo palmok nopunde makgi", "걷는 서 두 팔목 높은데 막기", "Walking stance, high double forearm block",
        "One forearm blocks high while the other backs it up."),
    Move("24", "Annun so orun palmok kaunde ap makgi, orun dung joomuk nopunde yop taerigi", "앉은 서 오른 팔목 가운데 앞 막기, 오른 등주먹 높은데 옆 때리기", "Sitting stance, middle front block, then high side back-fist strike",
        "Block in front with the forearm, then snap the back fist out to the side of the head."),
    Move("25", "Kaunde yopcha jirugi", "가운데 옆차 지르기", "Middle side piercing kick, right foot",
        "The first of two side kicks along the same line."),
    Move("26", "Kaunde yopcha jirugi", "가운데 옆차 지르기", "Middle side piercing kick, left foot",
        "The second side kick, with the other leg."),
    Move("27", "Niunja so kyocha sonkal momchau makgi", "니은자 서 교차 손칼 멈추어 막기", "L-stance, X knife-hand checking block",
        "Crossed knife-hands stop a kick before it lands."),
    Move("28", "Gunnun so sang sonbadak ollyo makgi", "걷는 서 쌍 손바닥 올려 막기", "Walking stance, twin palm upward block",
        "Both palms push upward to block a rising attack."),
    Move("29", "Gunnun so palmok chookyo makgi", "걷는 서 팔목 추켜 막기", "Walking stance, rising block",
        "Lift the forearm to block a strike to the head."),
    Move("30", "Gunnun so kaunde bandae jirugi", "걷는 서 가운데 반대 지르기", "Walking stance, middle reverse punch",
        "The pattern ends with a left-hand punch, marking Admiral Yi's death in battle."),
    Move("End", "Baro?", null, "Return to ready posture",
        "Bring the left foot back to the ready posture."),
    ),
)

val PATTERNS: List<Pattern> = listOf(CHOONG_MOO)
