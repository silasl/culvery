package uk.co.siland.culvery.capability.taekwondo

/** [hangul], [romanised] and [phonetic] are null for a move with no Korean name: the screen and the English voice use [english]. [toString] leaves out the text. */
data class Move(
    val number: String,
    val romanised: String?,
    val hangul: String?,
    val english: String,
    val description: String,
    /** What the Korean voice says, when it differs from what's shown (step 6 drops its "A"). */
    val spokenHangul: String? = hangul,
    /** The Korean name spelt for the English voice to say it close to Korean; a best guess from [hangul], not the instructor's. */
    val phonetic: String? = null,
) {
    /** The name the screen leads with. */
    val nameShown: String get() = hangul ?: romanised ?: english

    override fun toString() = "Move"
}

/** [toString] leaves out the name and the moves. */
data class Pattern(val name: String, val moves: List<Move>) {
    override fun toString() = "Pattern"
}

/**
 * The text of `culvery-notes/taekwondo-choong-moo.html` with the instructor's answers, straight apostrophes included. Every Hangul
 * entry is the plan's transliteration of the romanised ITF term: Hangul not confirmed by the instructor.
 */
val CHOONG_MOO = Pattern(
    "Choong-Moo",
    listOf(
    Move("Start", "Narani junbi sogi", "나란히 준비 서기", "Parallel ready stance",
        "Feet parallel, shoulder width apart; calm and ready to begin.", phonetic = "nah-rah-nee joon-bee suh-ghee"),
    Move("1", "Niunja so sang sonkal makgi", "니은자 서 쌍 손칼 막기", "L-stance, twin knife-hand block",
        "Both knife-hands block at once, guarding the front and the side.", phonetic = "nee-oon-jah suh sahng sohn-kahl mahk-ghee"),
    Move("2", "Gunnun so sonkal nopunde ap taerigi", "걷는 서 손칼 높은데 앞 때리기", "Walking stance, high front knife-hand strike",
        "Strike the neck with the knife-hand while the other hand guards the forehead.", phonetic = "gun-nun suh sohn-kahl noh-pun-deh ahp teh-ree-ghee"),
    Move("3", "Niunja so sonkal kaunde daebi makgi", "니은자 서 손칼 가운데 대비 막기", "L-stance, middle knife-hand guarding block",
        "Turn and block the middle section, the rear hand guarding the chest.", phonetic = "nee-oon-jah suh sohn-kahl kah-oon-deh deh-bee mahk-ghee"),
    Move("4", "Gunnun so opun sonkut nopunde tulgi", "걷는 서 엎은 손끝 높은데 뚫기", "Walking stance, high flat fingertip thrust",
        "Fingertips thrust at the eyes, palm facing down.", phonetic = "gun-nun suh uh-pun sohn-kut noh-pun-deh tool-kee"),
    Move("5", "Niunja so sonkal kaunde daebi makgi", "니은자 서 손칼 가운데 대비 막기", "L-stance, middle knife-hand guarding block",
        "Turn to face a new attacker and guard the middle section.", phonetic = "nee-oon-jah suh sohn-kahl kah-oon-deh deh-bee mahk-ghee"),
    Move("6", "Guburyo junbi sogi A", "구부려 준비 서기 A", "Bending ready stance A",
        "Stand on one leg, chambered for the kick that follows.", spokenHangul = "구부려 준비 서기", phonetic = "koo-boo-ryuh joon-bee suh-ghee"),
    Move("7", "Kaunde yopcha jirugi", "가운데 옆차 지르기", "Middle side piercing kick",
        "Drive the foot sword straight out to the side.", phonetic = "kah-oon-deh yup-chah jee-ruh-ghee"),
    Move("8", "Niunja so sonkal kaunde daebi makgi", "니은자 서 손칼 가운데 대비 막기", "L-stance, middle knife-hand guarding block",
        "Land from the kick and guard the middle section.", phonetic = "nee-oon-jah suh sohn-kahl kah-oon-deh deh-bee mahk-ghee"),
    Move("9", "Twimyo yopcha jirugi, wen niunja so sonkal kaunde daebi makgi", "뛰며 옆차 지르기, 왼 니은자 서 손칼 가운데 대비 막기", "Flying side piercing kick, then L-stance knife-hand guarding block",
        "Jump and kick in the air, then land balanced and guard at once.", phonetic = "twee-myuh yup-chah jee-ruh-ghee, wen nee-oon-jah suh sohn-kahl kah-oon-deh deh-bee mahk-ghee"),
    Move("10", "Niunja so palmok najunde yop makgi", "니은자 서 팔목 낮은데 옆 막기", "L-stance, low forearm side block",
        "Sweep a low kick aside with the forearm.", phonetic = "nee-oon-jah suh pahl-mohk nah-jun-deh yup mahk-ghee"),
    Move("11", null, null, "Walking stance, grab the opponent's head",
        "Reach up with both hands to seize the head."),
    Move("12", "Moorup ollyo chagi", "무릎 올려 차기", "Knee upward kick",
        "Pull the head down with both hands and drive the knee up into it.", phonetic = "moo-rup ohl-lyuh chah-ghee"),
    Move("13", "Gunnun so sonkal dung nopunde bandae ap taerigi", "걷는 서 손칼등 높은데 반대 앞 때리기", "Walking stance, high reverse ridge-hand strike",
        "The ridge of the hand swings in to the side of the head; the other hand rests under the elbow.", phonetic = "gun-nun suh sohn-kahl-dung noh-pun-deh bahn-deh ahp teh-ree-ghee"),
    Move("14", "Nopunde dollyo chagi", "높은데 돌려 차기", "High turning kick",
        "A circular kick to the head; flow straight into the next kick.", phonetic = "noh-pun-deh dohl-lyuh chah-ghee"),
    Move("15", "Kaunde dwitcha jirugi", "가운데 뒤차 지르기", "Middle back piercing kick",
        "Drive the heel straight back into the body, fast after the turning kick.", phonetic = "kah-oon-deh dwee-chah jee-ruh-ghee"),
    Move("16", "Niunja so palmok kaunde daebi makgi", "니은자 서 팔목 가운데 대비 막기", "L-stance, middle forearm guarding block",
        "Guard the middle section with both forearms.", phonetic = "nee-oon-jah suh pahl-mohk kah-oon-deh deh-bee mahk-ghee"),
    Move("17", "Kaunde dollyo chagi", "가운데 돌려 차기", "Middle turning kick",
        "A circular kick to the body.", phonetic = "kah-oon-deh dohl-lyuh chah-ghee"),
    Move("18", "Gojung so digutja makgi", "고정 서 디귿자 막기", "Fixed stance, U-shape block",
        "Both arms block high and low at once, as if stopping a staff.", phonetic = "koh-jung suh dee-gut-jah mahk-ghee"),
    Move("19", "Twigi, wen niunja so sonkal kaunde daebi makgi", "뛰기, 왼 니은자 서 손칼 가운데 대비 막기", "Jump and spin, then L-stance knife-hand guarding block",
        "Jump and spin a full turn, landing on the same spot ready to guard.", phonetic = "twee-ghee, wen nee-oon-jah suh sohn-kahl kah-oon-deh deh-bee mahk-ghee"),
    Move("20", "Gunnun so dwijibun sonkut najunde tulgi", "걷는 서 뒤집은 손끝 낮은데 뚫기", "Walking stance, low upset fingertip thrust",
        "Fingertips thrust up into the lower body, palm facing up.", phonetic = "gun-nun suh dwee-jee-bun sohn-kut nah-jun-deh tool-kee"),
    Move("21", "Niunja so dung joomuk baro yopdwi taerigi wa palmok najunde bandae makgi", "니은자 서 등주먹 바로 옆뒤 때리기와 팔목 낮은데 반대 막기", "L-stance, side back strike with the back fist and low block",
        "Strike behind you with the back fist while blocking low in front.", phonetic = "nee-oon-jah suh dung-joo-muk bah-roh yup-dwee teh-ree-ghee-wah pahl-mohk nah-jun-deh bahn-deh mahk-ghee"),
    Move("22", "Gunnun so sun sonkut kaunde tulgi", "걷는 서 선 손끝 가운데 뚫기", "Walking stance, middle straight fingertip thrust",
        "Straight fingertips thrust into the solar plexus.", phonetic = "gun-nun suh sun sohn-kut kah-oon-deh tool-kee"),
    Move("23", "Gunnun so doo palmok nopunde makgi", "걷는 서 두 팔목 높은데 막기", "Walking stance, high double forearm block",
        "One forearm blocks high while the other backs it up.", phonetic = "gun-nun suh doo pahl-mohk noh-pun-deh mahk-ghee"),
    Move("24", "Annun so orun palmok kaunde ap makgi, orun dung joomuk nopunde yop taerigi", "앉은 서 오른 팔목 가운데 앞 막기, 오른 등주먹 높은데 옆 때리기", "Sitting stance, middle front block, then high side back-fist strike",
        "Block in front with the forearm, then snap the back fist out to the side of the head.", phonetic = "ahn-jun suh oh-run pahl-mohk kah-oon-deh ahp mahk-ghee, oh-run dung-joo-muk noh-pun-deh yup teh-ree-ghee"),
    Move("25", "Kaunde yopcha jirugi", "가운데 옆차 지르기", "Middle side piercing kick, right foot",
        "The first of two side kicks along the same line.", phonetic = "kah-oon-deh yup-chah jee-ruh-ghee"),
    Move("26", "Kaunde yopcha jirugi", "가운데 옆차 지르기", "Middle side piercing kick, left foot",
        "The second side kick, with the other leg.", phonetic = "kah-oon-deh yup-chah jee-ruh-ghee"),
    Move("27", "Niunja so kyocha sonkal momchau makgi", "니은자 서 교차 손칼 멈추어 막기", "L-stance, X knife-hand checking block",
        "Crossed knife-hands stop a kick before it lands.", phonetic = "nee-oon-jah suh kyoh-chah sohn-kahl mum-choo-uh mahk-ghee"),
    Move("28", "Gunnun so sang sonbadak ollyo makgi", "걷는 서 쌍 손바닥 올려 막기", "Walking stance, twin palm upward block",
        "Both palms push upward to block a rising attack.", phonetic = "gun-nun suh sahng sohn-bah-dahk ohl-lyuh mahk-ghee"),
    Move("29", "Gunnun so palmok chookyo makgi", "걷는 서 팔목 추켜 막기", "Walking stance, rising block",
        "Lift the forearm to block a strike to the head.", phonetic = "gun-nun suh pahl-mohk choo-kyuh mahk-ghee"),
    Move("30", "Gunnun so kaunde bandae jirugi", "걷는 서 가운데 반대 지르기", "Walking stance, middle reverse punch",
        "The pattern ends with a left-hand punch, marking Admiral Yi's death in battle.", phonetic = "gun-nun suh kah-oon-deh bahn-deh jee-ruh-ghee"),
    Move("End", "Baro", "바로", "Return to ready posture",
        "Bring the left foot back to the ready posture.", phonetic = "bah-roh"),
    ),
)

val PATTERNS: List<Pattern> = listOf(CHOONG_MOO)
